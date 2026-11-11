(ns console.circle
  "Who is out there, what each machine is doing, and where a verb becomes a request.

   Presence is the word of the machine we administer: its roster — who it hears — is
   read on every poll, and each peer's tree is pulled from the address it names. A
   machine that falls silent keeps its last tree and reads off, so a class list never
   thins out mid-lesson; only a person removes one. Nothing persists."
  (:require [clojure.string  :as str]
            [ujima.circle.client :as client])
  (:import  [java.time LocalDateTime ZoneId]))


(def ^:private poll-ms   2000)
(def ^:private in-flight 256)   ;; trees fetched in one wave


(defonce ^:private cfg*    (atom {}))    ;; :key, :self-addr
(defonce ^:private self*   (atom nil))   ;; the machine we administer: {:id :address :tree}
(defonce ^:private roster* (atom {}))    ;; peer id -> {:address :state :tree}, as the machine lists them


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
  "Poll: the roster is a poll away, so a window opens onto machines."
  [{:keys [key self-addr] :or {self-addr "127.0.0.1"}}]
  (reset! cfg* {:key key :self-addr self-addr})
  (poll-loop!))
