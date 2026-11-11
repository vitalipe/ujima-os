(ns ujima.circle
  "Who else is in the circle. Every machine broadcasts a heartbeat signed with the circle key
   over UDP on the API's port, one per network it is on, naming its own address there; the
   roster is who was heard, where, and how recently. Presence only — what a peer is doing is
   its own machine tree, read from the address kept here."
  (:require [clojure.edn        :as edn]
            [lib.hmac           :as hmac]
            [ujima.linux.net    :as net]
            [ujima.log          :as log])
  (:import  [java.net DatagramPacket DatagramSocket InetAddress]))


(def ^:private beat-ms    2000)
(def ^:private unsure-ms  3000)   ;; one beat missed
(def ^:private offline-ms 7000)   ;; three missed
(def ^:private beat-tag   "peer-beat-v1")
(def ^:private packet-max 512)

(defonce ^:private roster* (atom {}))   ;; id -> {:host :port :boot :seq :seen-ms}, all but seen-ms signed


;; ── the beat ────────────────────────────────────────────────────────────────

(defn- beat-fields [{:keys [id host port boot seq]}] [id host port boot seq])

(defn encode
  "The datagram for BEAT, signed with KEY."
  [key beat]
  (pr-str (assoc beat :sig (hmac/sign key beat-tag (beat-fields beat)))))

(defn decode
  "The beat in TEXT when it is well-formed and signed with KEY; nil for anything else."
  [key text]
  (let [{:keys [id host port boot seq sig] :as beat} (try (edn/read-string text) (catch Exception _ nil))]
    (when (and (string? id) (string? host) (int? port) (string? boot) (int? seq)
               (try (hmac/verify key beat-tag (beat-fields beat) sig) (catch Exception _ false)))
      (dissoc beat :sig))))


;; ── the roster ──────────────────────────────────────────────────────────────

(defn- admit
  "ROSTER after hearing BEAT at NOW. A beat no later than the last from the same boot is
   one already heard — delayed, or replayed — and changes nothing."
  [roster {:keys [id host port boot seq]} now]
  (let [known (roster id)]
    (if (and known (= boot (:boot known)) (<= seq (:seq known)))
      roster
      (assoc roster id {:host host :port port :boot boot :seq seq :seen-ms now}))))

(defn- state-of [seen-ms now]
  (let [age (- now seen-ms)]
    (cond (<= age unsure-ms)  :online
          (<= age offline-ms) :unsure
          :otherwise          :offline)))

(defn- render [roster now]
  (->> roster
       (map (fn [[id {:keys [host port seen-ms]}]]
              {:id id :address {:host host :port port} :state (state-of seen-ms now)}))
       (sort-by :id)
       vec))

(defn snapshot
  "Every machine heard, by id, with its address and how present it is."
  []
  (render @roster* (System/currentTimeMillis)))

(defn forget!
  "Drop ID; it returns with its next beat."
  [id]
  (swap! roster* dissoc id)
  nil)

(defn- heard!
  "A beat into the roster — unless it is our own, or it names an address other than the
   one it came from: a captured beat verifies only where it was issued."
  [{:keys [key id]} source text]
  (when-let [beat (decode key text)]
    (when (and (not= id (:id beat)) (= source (:host beat)))
      (swap! roster* admit beat (System/currentTimeMillis)))))


;; ── the wire ────────────────────────────────────────────────────────────────

(defn- listen!
  "Every datagram on SOCK into the roster, for as long as the socket is open."
  [sock cfg]
  (future
    (let [buf (byte-array packet-max)]
      (loop []
        (let [packet (DatagramPacket. buf packet-max)]
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
    (let [bytes (.getBytes ^String (encode key (assoc beat :host host)) "UTF-8")]
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
                     (send-all! sock key {:id id :port port :boot boot :seq n} nets port)
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
