(ns console.circle
  "Who is out there, what each machine is doing, and where a verb becomes a request.

   Presence is the word of the machine we administer: its roster — who it hears — is
   read on every poll, and each peer's tree is pulled from the address it names. A
   machine that falls silent keeps its last tree and reads off, so a class list never
   thins out mid-lesson; only a person removes one. The sweep — mDNS, then the
   subnet — feeds nothing: it is the diagnostic for a machine that answers but is not
   heard. Nothing persists."
  (:require [clojure.string  :as str]
            [ujima.circle.client :as client]
            [lib.task        :as task]
            [ujima.linux.net.mdns :as mdns])
  (:import  [java.time LocalDateTime ZoneId]))


(def ^:private poll-ms   2000)
(def ^:private in-flight 256)   ;; a /24 in one wave — measured 3s on a classroom AP, 8s at 64

(def ^:private mdns-service "_ujima._tcp")   ;; what a ujima machine publishes about itself


(defonce ^:private cfg*    (atom {}))    ;; :key, :self-addr
(defonce ^:private self*   (atom nil))   ;; the machine we administer: {:id :address :tree}
(defonce ^:private roster* (atom {}))    ;; peer id -> {:address :state :tree}, as the machine lists them
(defonce ^:private scan*   (atom nil))   ;; {:current {:task :found :foreign :unheard}, :next {:task}} — the one sweep + at most one queued
(defonce ^:private lock    (Object.))


(defn- self-peer []
  (let [{:keys [key self-addr]} @cfg*] {:key key :address {:host self-addr}}))

(defn- peer-of [id]
  (when-let [{:keys [address]} (if (= id (:id @self*)) @self* (@roster* id))]
    {:key (:key @cfg*) :id id :address address}))

;; bb futures, never a raw executor — SCI on one stops silently
(defn- in-waves [f xs]
  (mapcat (fn [chunk] (mapv deref (mapv #(future (f %)) chunk)))
          (partition-all in-flight xs)))


;; ── the poll ────────────────────────────────────────────────────────────────

(defn- refresh! [id]
  (when-let [peer (peer-of id)]
    (let [{:keys [status data]} (client/machine peer)]
      (when (= :ok status)
        (if (= id (:id @self*))
          (swap! self* assoc :tree data)
          (swap! roster* update id assoc :tree data))))))

(defn- adopt!
  "The machine's roster over ours: presence is its word, a peer it no longer lists is
   off, and the trees stay ours to keep."
  [peers]
  (let [heard (into {} (map (fn [{:keys [id] :as entry}] [id (dissoc entry :id)])) peers)]
    (swap! roster* (fn [roster]
                     (merge-with merge
                                 (into {} (map (fn [[id entry]] [id (assoc entry :state :offline)])) roster)
                                 heard)))))

(defn- poll-self!
  "The machine we administer: its id and tree, then who it hears."
  []
  (let [{:keys [status data]} (client/machine (self-peer))]
    (when (and (= :ok status) (:id data))
      (reset! self* {:id (:id data) :address (:address (self-peer)) :tree data})
      (let [{:keys [status data]} (client/peers (peer-of (:id data)))]
        (when (= :ok status) (adopt! data))))))

(defn- poll-once! []
  (poll-self!)
  (doall (in-waves refresh! (for [[id {:keys [state]}] @roster* :when (not= :offline state)] id))))

(defn- poll-loop! []
  (future
    (loop []
      (try (poll-once!) (catch Throwable _ nil))
      (Thread/sleep poll-ms)
      (recur))))


;; ── the sweep: a diagnostic ─────────────────────────────────────────────────

(defn- ip->long [ip]
  (reduce (fn [acc octet] (+ (* acc 256) (parse-long octet))) 0 (str/split ip #"\.")))

(defn- long->ip [n]
  (str/join "." (for [shift [24 16 8 0]] (bit-and (bit-shift-right n shift) 255))))

(defn- hosts
  "Every host address on this network. Never wider than a /24 — a class is one
   subnet, and sweeping a /16 is 65k probes nobody asked for."
  [ip prefix]
  (let [bits (- 32 (max (or prefix 24) 24))
        span (bit-shift-left 1 bits)
        base (bit-and (ip->long ip) (bit-not (dec span)))]
    (map #(long->ip (+ base %)) (range 1 (dec span)))))

(defn- our-network
  "Asked of the machine we administer — babashka has no NetworkInterface."
  []
  (let [{:keys [status data]} (client/machine (self-peer))
        ip                    (get-in data [:net :ip])]
    (when (and (= :ok status) ip)
      {:ip     ip
       :prefix (some (fn [[_ i]] (when (= ip (:ip i)) (:prefix i)))
                     (get-in data [:net :interfaces]))})))

(defn- probe-all
  "Probe ADDRS, twice for the silent — a dropped probe is a missing machine."
  [key addrs]
  (let [probe  (fn [addr] [addr (client/probe key addr)])
        once   (in-waves probe addrs)
        silent (for [[addr r] once :when (= :noreply (:status r))] addr)]
    (concat (remove (fn [[_ r]] (= :noreply (:status r))) once)
            (in-waves probe silent))))

(defn- tally
  "What the probes say against what the machine hears. FOUND verified as ours — by id,
   since one machine answers on every address it has; FOREIGN hold another key; UNHEARD
   are ours and answering, yet on nobody's roster: the broadcast is not getting through."
  [answers]
  (let [ours    (into #{} (keep (fn [[_ r]] (when (= :ok (:status r)) (:id (:data r))))) answers)
        heard   (into #{(:id @self*)} (for [[id {:keys [state]}] @roster* :when (not= :offline state)] id))
        foreign (count (filter (fn [[_ r]] (= :auth/bad-response (:reason (:data r)))) answers))]
    {:found   (count ours)
     :foreign foreign
     :unheard (count (remove heard ours))}))

(defn- sweep!
  "The announced first, then the subnet. mDNS costs one multicast query; the sweep is
   what reaches a machine on a network that drops multicast, and addresses nothing
   announced."
  []
  (let [{:keys [key]}       @cfg*
        {:keys [ip prefix]} (our-network)
        heard               (mdns/browse mdns-service)
        answers             (concat (probe-all key heard)
                                    (when ip (probe-all key (remove (set heard) (hosts ip prefix)))))]
    (cond-> (tally answers)
      (nil? ip) (assoc :reason :no-network))))


(defn- ->scan-task []
  (task/->task :discover
               (fn [_] (let [r (sweep!)]
                         (swap! scan* update :current merge r)
                         r))))

(defn- current-task [] (get-in @scan* [:current :task]))
(defn- queued-task  [] (get-in @scan* [:next :task]))

(defn- sweeping? []
  (when-let [t (current-task)] (not (task/finished? t))))

(defn- run-scan!
  ;; caller holds the lock: t becomes THE sweep and starts
  [t]
  (reset! scan* {:current {:task t}})
  (task/run! t)
  t)

(defn- chain!
  ;; when the running sweep ends, the queued one takes its place — unless a
  ;; rescan! promoted it first (run-scan! clears :next, so the check fails)
  [running queued]
  (future
    (while (not (task/finished? running)) (Thread/sleep 100))
    (locking lock
      (when (= queued (queued-task))
        (run-scan! queued)))))

(defn scan
  "What the current or last sweep is doing — the panels' :scan field."
  []
  (when-let [{:keys [task found foreign unheard]} (:current @scan*)]
    {:id      (:id task)
     :running (not (task/finished? task))
     :found   found
     :foreign foreign
     :unheard unheard}))

(defn rescan!
  "A person asks: their sweep must start no earlier than the ask. Idle = run one
   now; running = queue ONE next (every asker shares it) and answer its id — the
   caller watches for that id to finish. Never two sweeps in flight."
  []
  (locking lock
    (cond
      (not (sweeping?)) (do (run-scan! (or (queued-task) (->scan-task))) (scan))
      (queued-task)     {:id (:id (queued-task)) :running true}
      :else             (let [t (->scan-task)]
                          (swap! scan* assoc :next {:task t})
                          (chain! (current-task) t)
                          {:id (:id t) :running true}))))


;; ── verbs ───────────────────────────────────────────────────────────────────

(defn- epoch-ms
  "The panel sends wall time and a zone; the wire takes an instant."
  [{:keys [tz date time]}]
  (-> (LocalDateTime/parse (str date "T" time))
      (.atZone (ZoneId/of tz))
      (.toInstant)
      (.toEpochMilli)))

(defn- ->request
  "Verb -> [command-path body], nil for one we cannot send. Circle writes :activity, over
   the :session the machine's own bar writes"
  [verb args]
  (case verb
    :mute      ["settings/audio/muted"        {:scope "activity" :value true}]
    :unmute    ["settings/audio/muted"        {:scope "activity" :value false}] ;; a clear would leave a machine its own user muted still muted
    :volume    ["audio/volume"                {:scope "activity" :value (:value args)}]
    :open-app  ["app/open"                    {:app (:app args)}]
    ;; "current" is the machine's own app, resolved there — the panel names it, never resolves it
    :focus     ["desktop/focus"               (when (not= "current" (:app args)) {:app (:app args)})]
    :release   ["desktop/release"             nil]
    :close-app ["app/close"                   nil]   ;; closing lets go of the hold on its own

    :open-url  ["app/open-url"                {:url (:url args)}]
    :lock      ["desktop/lock"                nil]
    :unlock    ["desktop/unlock"              nil]
    :restart   ["system/restart"              nil]
    :poweroff  ["system/poweroff"             nil]
    :clock/set ["system/clock"                {:epoch (epoch-ms args) :timezone (:tz args)}]
    nil))

(defn- fold
  "Several requests, one reply: silence outranks refusal, refusal outranks success."
  [replies]
  (let [status (map :status replies)]
    (cond (some #{:noreply} status) {:status :noreply :data {:reason :transport}}
          (some #{:fail} status)    (first (filter #(= :fail (:status %)) replies))
          :otherwise                {:status :ok})))

(defn- write-settings! [peer writes]
  (fold (for [[path value] writes]
          (client/command! peer (str "settings/" (str/join "/" (map name path)))
                        {:scope "device" :value value}))))

(defn send!
  "One verb, one machine, blocking — jobs fans out and holds the deadline. An :ok
   refetches at once, so a panel never waits out a poll to see what it just did."
  [id verb args]
  (if-let [peer (peer-of id)]
    (let [reply (case verb
                  :settings/write (write-settings! peer (:writes args))
                  (if-let [[path body] (try (->request verb args)
                                            (catch Exception _ nil))]
                    (client/command! peer path body)
                    {:status :fail :data {:reason :unknown-verb}}))]
      (when (= :ok (:status reply)) (refresh! id))
      reply)
    {:status :noreply :data {:reason :unknown-machine}}))


;; ── what the panels read ────────────────────────────────────────────────────

(defn self [] (:id @self*))

(defn- tail [id] (subs id (max 0 (- (count id) 4))))

(defn- labelled
  "The machine's name; its id's tail when it has none; both when two share a name.
   Needs the whole roster, so it is not a fact about one peer."
  [peers]
  (let [shared (->> peers
                    (keep #(some-> (get-in % [:system :name]) str/lower-case))
                    frequencies
                    (keep (fn [[name n]] (when (> n 1) name)))
                    set)]
    (mapv (fn [{:keys [id] :as p}]
            (let [host (get-in p [:system :name])]
              (assoc p :label (cond (str/blank? host)                  (tail id)
                                    (shared (str/lower-case host))     (str host " " (tail id))
                                    :otherwise                          host))))
          peers)))

(defn peers
  "Each tree as it last answered, plus what a machine cannot say about itself."
  []
  (let [{:keys [id address tree]} @self*]
    (->> (cond-> @roster* tree (assoc id {:address address :state :online :tree tree}))
         (keep (fn [[id {:keys [tree address state]}]]
                 (when tree (assoc tree :id id :address address :online (not= :offline state)))))
         (sort-by :id)
         vec
         labelled)))

(defn forget!
  "Drop it — here, and on the machine that hears for us. It returns when heard again."
  [id]
  (let [reply (client/command! (peer-of (self)) "peers/forget" {:id id})]
    (if (= :ok (:status reply))
      (do (swap! roster* dissoc id)
          {:removed id})
      reply)))

(defn init!
  "Poll — the roster is a poll away, so a window opens onto machines — and sweep
   once, so the diagnostic has an answer before anyone asks for one."
  [{:keys [key self-addr] :or {self-addr "127.0.0.1"}}]
  (reset! cfg* {:key key :self-addr self-addr})
  (poll-loop!)
  (rescan!))
