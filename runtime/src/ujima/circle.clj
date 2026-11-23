(ns ujima.circle
  "Who else is in the circle, and what each one offers. Every machine broadcasts a heartbeat
   signed with the circle key over UDP on the API's port, one per network it is on, naming
   its own address there; the peers are who was heard, where, and how recently. The beat
   carries the hash of what the machine publishes (ujima.circle.self), so a peer's name and
   shares are read off its tree once when it appears and again when the hash moves, never
   every beat. A pass runs whole under one lock — observe (a beat into the peers), act (a
   probe per peer, asked again when its beat moved), project (hand (next prev) to
   targets when it moved) — on a beat, on the tick, and when a read lands. The datagram is
   ujima.circle.beat, the peers as data ujima.circle.peers; this is the sockets, the
   threads, the clock and the reads."
  (:require [clojure.core.async  :as async]
            [lib.hmac            :as hmac]
            [lib.task            :as task]
            [lib.task.flow       :refer [flow <join!]]
            [lib.task.probe      :as probe]
            [lib.task.timeline   :as timeline]
            [ujima.circle.beat   :as beat]
            [ujima.circle.client :as client]
            [ujima.circle.peers  :as peers]
            [ujima.circle.self   :as self]
            [ujima.linux.net     :as net]
            [ujima.log           :as log])
  (:import  [java.net DatagramPacket DatagramSocket InetAddress]))


(def ^:private beat-ms  2000)
(def ^:private retry-ms 30000)   ; a read that failed is tried again after this

(defonce ^:private peers*   (atom {}))   ; id -> the beat + :seen-ms (ujima.circle.peers)
(defonce ^:private reads*   (atom {}))   ; id -> {:args :probe}: the ask, and who asks
(defonce ^:private prev*    (atom nil))  ; last projection, the prev of (next prev)
(defonce ^:private cfg*     (atom {}))   ; :key :id by init!
(defonce ^:private targets* (atom []))
(defonce ^:private lock     (Object.))   ; every write path holds this


(defn- now [] (System/currentTimeMillis))


;; ── what a peer publishes ────────────────────────────────────────────────────

(defn- answered? [[_path {:keys [status]}]] (= :ok status))

(defn- ->read-task
  "PEER's name and shares off its tree: {:name \"Mvule\" :shares [{:name \"math\" :access :rw}]}."
  [peer]
  (flow :circle/read
    ;; FIXME: two requests until the http side can pick fields
    (let [answers (into {} (for [path ["system/name" "shares"]] [path (client/node peer path)]))]
      (doseq [[path {:keys [data]}] (remove answered? answers)]
        (error! :circle/unread (str path ": " (pr-str (:reason data)))))

      {:name   (get-in answers ["system/name" :data :name])
       :shares (into [] (get-in answers ["shares" :data]))})))

(defn- ->read-probe
  "The probe that reads peer ID, asked with where to: {:host \"10.0.0.5\" :port 1337 …}."
  [id]
  (probe/->probe (fn [{:keys [host port]}]
                   (->read-task {:key (:key @cfg*) :id id :address {:host host :port port}}))))

(defn- ask
  "The beat without its counter: a new address, boot or hash is a new ask, a new seq is not."
  [peer]
  (select-keys peer [:host :port :boot :hash]))

(defn- failed-at
  "When ATTEMPT recorded its :error, in millis."
  [attempt]
  (.toEpochMilli ^java.time.Instant
                 (:time (timeline/timeline->last-of (task/task->timeline attempt) (:id attempt)))))


(declare act! project!)

(defn- start-read!
  "The read runs off the lock, the pass after it under it. Same boot, same probe; a new
   boot's session is new, so is its probe."
  [id peer]
  (let [args    (ask peer)
        read    (@reads* id)
        probe   (if (= (:boot args) (get-in read [:args :boot])) (:probe read) (->read-probe id))
        attempt (probe/run! probe args)]

    (swap! reads* assoc id {:args args :probe probe})

    (task/run!
      (flow :circle/land

        (try (<join! 100 attempt)
             (catch Throwable e
               (log/warn "circle: could not read a peer"
                         {:id id :host (:host args) :error (ex-message (:error (:child-error (ex-data e))))})))

        (try (locking lock (act!) (project!))
             (catch Throwable e
               (log/error "circle: the pass after a read failed" {:id id :error (ex-message e)})))))))


(defn- wanted?
  "Not offline, and nothing asked of this beat yet, or the ask failed more than retry-ms ago."
  [now reads [id {:keys [seen-ms] :as peer}]]
  (let [{:keys [args probe]} (reads id)]
    (and (not= :offline (peers/state-of seen-ms now))
         (or (nil? probe)
             (not= args (ask peer))
             (and (= :error (probe/probe->state probe))
                  (<= (+ (failed-at (probe/probe->task probe)) retry-ms) now))))))

(defn- profiles []
  (into {} (for [[id {:keys [probe]}] @reads*
                 :let  [profile @probe]
                 :when profile]
             [id profile])))


;; ── the pass ────────────────────────────────────────────────────────────────

(defn- observe!
  "A beat into the peers. State only; act! does the rest."
  [beat]
  (swap! peers* peers/admit beat (now)))

(defn- act!
  "A read for every peer that wants one, none for a peer no longer known. A read lands on
   its own thread, which acts and projects again."
  []
  (let [now (now) peers @peers*]
    (swap! reads* select-keys (keys peers))
    (doseq [[id peer] (filter (partial wanted? now @reads*) peers)]
      (start-read! id peer))))

(defn- project!
  "Targets hear (next prev) only when the projection moved: a beat with nothing new, and the
   tick, project the same value."
  []
  (let [next (peers/render @peers* (profiles) (now))
        prev @prev*]
    (reset! prev* next)
    (when (not= next prev)
      (doseq [t @targets*] (t next prev)))))

(defn- tick! [] (locking lock (act!) (project!)))


;; ── the peers ───────────────────────────────────────────────────────────────

(defn snapshot
  "Every machine heard, by id: its address, how present it is, its name and shares once read."
  []
  (or @prev* []))

(defn nearby
  "Every share a present peer offers, as places to mount."
  []
  (peers/nearby (snapshot)))

(defn forget!
  "Drop ID and its read; it returns with its next beat."
  [id]
  (locking lock
    (swap! peers* dissoc id)
    (act!) (project!))
  nil)

(defn- heard!
  "A beat into the peers — unless it is our own, or it names an address other than the
   one it came from: a captured beat verifies only where it was issued."
  [source text]
  (let [{:keys [key id]} @cfg*]
    (when-let [beat (beat/decode key text)]
      (when (and (not= id (:id beat)) (= source (:host beat)))
        (locking lock
          (observe! beat)
          (act!)
          (project!))))))


;; ── the wire ────────────────────────────────────────────────────────────────

(defn- listen!
  "Every datagram on SOCK into the peers, for as long as the socket is open."
  [sock]
  (future
    (let [buf (byte-array beat/packet-max)]
      (loop []
        (let [packet (DatagramPacket. buf beat/packet-max)]
          (try (.receive sock packet)
               (heard! (.getHostAddress (.getAddress packet))
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
  "Announce every beat-ms, then run the pass: the tick is how a peer that went quiet reads
   as unsure, then offline. A send that keeps failing is logged once, not every beat."
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
          (try (tick!)
               (catch Throwable e (log/error "circle: the pass on the tick failed" {:error (ex-message e)})))
          (Thread/sleep beat-ms)
          (recur (inc n) nets failed))))))


;; ── wiring ──────────────────────────────────────────────────────────────────

(defn on-project!
  "Attach a target: F gets (next prev) under the lock when the peers moved. A target that
   runs another plane's pass is handed off (`watch-peers!`) — two planes waiting on each
   other's lock is a deadlock."
  [f]
  (swap! targets* conj f)
  nil)

(defn watch-peers!
  "The present peers as a watcher channel, for another plane's pass off this lock:
     {:type :circle/peers :peers #{\"87f6…\" …}}
   one event when the set moved, none for a read that landed; a slow consumer sees the latest."
  []
  (let [ch   (async/chan (async/sliding-buffer 1))
        seen (atom nil)]
    (on-project! (fn [next _prev]
                   (let [present (into #{} (map :id) (peers/present next))]
                     (when (not= present @seen)
                       (reset! seen present)
                       (async/>!! ch {:type :circle/peers :peers present})))))
    ch))

(defn- reset-state! [cfg]
  (reset! peers* {})
  (reset! reads* {})
  (reset! prev*  nil)
  (reset! cfg*   (select-keys cfg [:key :id])))

(defn init!
  "Listen on PORT, and announce when there is an ID to announce. A taken port throws, as
   the API's own does."
  [{:keys [port id] :as cfg}]
  (reset-state! cfg)
  (listen! (DatagramSocket. port))
  (if id
    (beat! cfg)
    (log/warn "circle: this machine has no id — listening, not announcing")))
