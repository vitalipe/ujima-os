(ns ujima.circle
  "Who else is in the circle. Every machine broadcasts a heartbeat signed with the circle key
   over UDP on the API's port, one per network it is on, naming its own address there; the
   peers are who was heard, where, and how recently. Presence only — what a peer is doing is
   its own machine tree, read from the address kept here; the beat carries the hash of what
   this machine publishes there (ujima.circle.self), so a peer re-reads it when the hash
   moves, not every beat. The datagram is ujima.circle.beat, the peers as data
   ujima.circle.peers; this is the sockets, the threads and the clock."
  (:require [lib.hmac            :as hmac]
            [ujima.circle.beat   :as beat]
            [ujima.circle.peers  :as peers]
            [ujima.circle.self   :as self]
            [ujima.linux.net     :as net]
            [ujima.log           :as log])
  (:import  [java.net DatagramPacket DatagramSocket InetAddress]))


(def ^:private beat-ms 2000)

(defonce ^:private peers* (atom {}))


;; ── the peers ───────────────────────────────────────────────────────────────

(defn snapshot
  "Every machine heard, by id, with its address and how present it is."
  []
  (peers/render @peers* (System/currentTimeMillis)))

(defn forget!
  "Drop ID; it returns with its next beat."
  [id]
  (swap! peers* dissoc id)
  nil)

(defn- heard!
  "A beat into the peers — unless it is our own, or it names an address other than the
   one it came from: a captured beat verifies only where it was issued."
  [{:keys [key id]} source text]
  (when-let [beat (beat/decode key text)]
    (when (and (not= id (:id beat)) (= source (:host beat)))
      (swap! peers* peers/admit beat (System/currentTimeMillis)))))


;; ── the wire ────────────────────────────────────────────────────────────────

(defn- listen!
  "Every datagram on SOCK into the peers, for as long as the socket is open."
  [sock cfg]
  (future
    (let [buf (byte-array beat/packet-max)]
      (loop []
        (let [packet (DatagramPacket. buf beat/packet-max)]
          (try (.receive sock packet)
               (heard! cfg (.getHostAddress (.getAddress packet))
                       (String. (.getData packet) 0 (.getLength packet) "UTF-8"))
               (catch Throwable e
                 (when-not (.isClosed sock)
                   (log/warn "circle: a datagram was dropped" {:error (ex-message e)}))))
          (when-not (.isClosed sock) (recur)))))))


(defn- send-all!
  "BEAT once per network, naming our address on it, to that network's broadcast."
  [sock key beat networks port]
  (doseq [{:keys [host to]} networks]
    (let [bytes (.getBytes ^String (beat/encode key (assoc beat :host host)) "UTF-8")]
      (.send sock (DatagramPacket. bytes (count bytes) to port)))))

(defn- networks
  "Every network we are on — read again every 30 s, and every 10 s while there is none, so
   a machine that joins wifi after boot is soon announcing."
  [n known]
  (if (zero? (mod n (if (empty? known) 5 15)))
    (mapv (fn [{:keys [host broadcast]}] {:host host :to (InetAddress/getByName broadcast)})
          (net/networks (net/interface-facts)))
    known))

(defn- beat!
  "Announce every beat-ms. A send that keeps failing is logged once, not every beat."
  [{:keys [key id port]}]
  (let [sock (doto (DatagramSocket.) (.setBroadcast true))
        boot (hmac/nonce)]
    (future
      (loop [n 0 nets [] failed nil]
        (let [[nets failed]
              (try (let [nets (networks n nets)]
                     (send-all! sock key {:id id :port port :boot boot :seq n :hash (:hash (self/snapshot))} nets port)
                     (when failed (log/info "circle: announcing again"))
                     [nets nil])
                   (catch Throwable e
                     (let [why (ex-message e)]
                       (when (not= why failed) (log/warn "circle: beat not sent" {:error why}))
                       [nets why])))]
          (Thread/sleep beat-ms)
          (recur (inc n) nets failed))))))


(defn init!
  "Listen on PORT, and announce when there is an ID to announce. A taken port throws, as
   the API's own does."
  [{:keys [port id] :as cfg}]
  (listen! (DatagramSocket. port) cfg)
  (if id
    (beat! cfg)
    (log/warn "circle: this machine has no id — listening, not announcing")))
