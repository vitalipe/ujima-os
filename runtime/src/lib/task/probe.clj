(ns lib.task.probe
  "A question asked again: a probe runs a fresh task per ask and keeps the last answer.

     (def p (->probe (fn [url] (flow :fetch (http/get url)))))   ; args -> a cold task
     (run!! p \"http://a\")   ; one attempt, on this thread
     @p                       ; => its answer, kept while the next attempt runs
     (run! p \"http://b\")    ; another attempt, on its own thread
     (probe->state p)         ; => :running — :new until the first run
     (probe->task p)          ; the attempt, for the task API"
  (:require [lib.task          :as task]
            [lib.task.timeline :as timeline]))


(defn- state-of [attempt] (some-> attempt task/task->state))

(defn- result-of [attempt]
  (:payload (timeline/timeline->last-of (task/task->timeline attempt) (:id attempt))))

(defn- answer
  "LAST, or the current attempt's answer when it has one."
  [{:keys [attempt last]}]
  (if (= :done (state-of attempt)) (result-of attempt) last))


(defrecord Probe [maker state*]   ; state* = {:attempt t :last v}
  clojure.lang.IDeref
  (deref [_] (answer @state*)))


(defn ->probe
  "A cold probe over MAKER, a fn of the args to a cold task."
  [maker]
  (->Probe maker (atom {:attempt nil :last nil})))

(defn probe? [x] (instance? Probe x))

(defn probe->task
  "The current attempt, nil before the first run."
  [{:keys [state*]}]
  (:attempt @state*))

(defn probe->state
  "The current attempt's state: :new until the first run, then :running, :done or :error."
  [p]
  (or (state-of (probe->task p)) :new))


(defn- attempt!
  "A new attempt from ARGS, installed; the answer of the one it replaces is kept."
  [{:keys [maker state*]} args]
  (let [attempt (apply maker args)]
    (swap! state* (fn [state] {:attempt attempt :last (answer state)}))
    attempt))

(defn run!
  "Make an attempt from ARGS and start it on its own thread. Returns the attempt."
  [p & args]
  (task/run! (attempt! p args)))

(defn run!!
  "Make an attempt from ARGS and run it on this thread. Returns its timeline."
  [p & args]
  (task/run!! (attempt! p args)))
