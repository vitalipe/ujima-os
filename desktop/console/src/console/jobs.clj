(ns console.jobs
  "Jobs over a send fn. One act = one lib.task: per-target replies
   append :peer-result events as they arrive, and the reply deadline appends
   :noreply for the silent ones. A job view is a reduce of that timeline —
   per-verb meaning (restart's :ok reads as :accepted) lives only here.
   A reply is a keyword (:ok :fail :noreply) or {:status kw :data {...}} when
   the verb returns data (checks). Jobs carry the :app that started them
   (:circle | :setup) so each panel's view sees only its own actions."
  (:require [lib.task          :as task]
            [lib.task.timeline :as timeline :refer [->TimelineEvent]]))


(def ^:private reply-timeout-ms 8500)   ;; a hair over the 8s command transport — a job never gives up first
(def ^:private keep-jobs        32)

(defonce ^:private jobs* (atom {}))   ;; job id -> {:task :verb :targets :app}


(defn- reply-status [reply] (if (map? reply) (:status reply) reply))
(defn- reply-data   [reply] (when (map? reply) (:data reply)))

(defn- result-event [{:keys [id name]} peer reply]
  (->TimelineEvent id [name] :peer-result (java.time.Instant/now) {:peer peer :reply reply}))

(defn- unanswered? [timeline peer]
  (not-any? #(and (= :peer-result (:type %))
                  (= peer (get-in % [:payload :peer])))
            timeline))

(defn- fanout [send! verb targets args]
  (fn [t]
    (let [reply!   (fn [peer reply]
                     (task/event! t (result-event t peer reply) #(unanswered? % peer)))
          futs     (doall (for [peer targets]
                            (future
                              (let [reply (send! peer verb args)]
                                (when (not= :noreply (reply-status reply))
                                  (reply! peer reply))))))
          deadline (+ (System/currentTimeMillis) reply-timeout-ms)]
      (doseq [f futs]
        (deref f (max 1 (- deadline (System/currentTimeMillis))) :timeout))
      (doseq [peer targets]
        (reply! peer :noreply))
      nil)))


(defn- remember! [t entry]
  (swap! jobs* (fn [jobs]
                 (->> (assoc jobs (:id t) entry)
                      (sort-by key)
                      (take-last keep-jobs)
                      (into {})))))


(defn act!
  "Starts verb against targets on an async thread; returns the job id."
  [send! app verb targets args]
  (let [t (task/->task verb (fanout send! verb targets args))]
    (remember! t {:task t :verb verb :targets targets :app app})
    (task/run! t)
    (:id t)))


(defn run-local!
  "A cold lib.task the console runs itself — no peer, so no targets; its own progress is the view."
  [app verb t]
  (remember! t {:task t :verb verb :targets [] :app app :local true})
  (task/run! t)
  (:id t))


(defn- shown-status [verb status]
  (if (and (#{:restart :poweroff} verb) (= :ok status)) :accepted status))


(defn- local-view
  "A local job's progress, its last message, and how it ended."
  [task]
  (let [tl     (task/task->timeline task)
        id     (:id task)
        latest (fn [type] (:payload (timeline/timeline->last-of-type tl id type)))
        error  (latest :error)]
    (cond-> {:progress (timeline/timeline->progress tl)
             :message  (:message (latest :progress))}
      error          (assoc :error (or (ex-message (:error error)) (:message error)))
      (latest :done) (assoc :result (latest :done)))))


(defn- job-view [{:keys [task verb targets app local]}]
  (let [replies (into {}
                      (comp (filter #(= :peer-result (:type %)))
                            (map (fn [{{:keys [peer reply]} :payload}]
                                   [peer reply])))
                      (task/task->timeline task))
        data    (into {} (keep (fn [[peer reply]]
                                 (when-let [d (reply-data reply)] [peer d])))
                      replies)]
    (cond-> {:job      (:id task)
             :verb     verb
             :app      app
             :finished (boolean (task/finished? task))
             :peers    (into {}
                             (map (fn [id]
                                    [id (if-let [reply (get replies id)]
                                          (shown-status verb (reply-status reply))
                                          :pending)]))
                             targets)}
      (seq data) (assoc :data data)
      local      (merge (local-view task)))))

(defn job [id]
  (some-> (get @jobs* id) job-view))

(defn jobs []
  (->> @jobs* (sort-by key) vals (mapv job-view)))

(defn- app-jobs [app]
  (->> @jobs* (sort-by key) vals (filter #(= app (:app %)))))


(defn latest-action
  "The app's most recent job, running or not — a failure stays readable after it ends, which
   `active-action` (the busy gate) cannot do."
  [app]
  (some-> (last (app-jobs app)) job-view))


(defn active-action
  "The app's single running action: its latest unfinished job, nil when idle."
  [app]
  (some-> (->> @jobs*
               (sort-by key)
               vals
               (filter #(= app (:app %)))
               (remove #(task/finished? (:task %)))
               last)
          job-view))
