(ns ujima.desktop.guard
  "The memory-pressure guard: a thrashing machine gets no help from the kernel, so kill the app
   giving back the most memory, spare the one on screen, judge the next reading by what happened
   since the kill."
  (:require [ujima.log :as log]
            [ujima.desktop.app   :as app]
            [ujima.linux.memory  :as memory]
            [ujima.linux.systemd :as systemd]))


(defonce ^:private last-kill* (atom nil))   ; {:at ms :total PSI µs}

(def settle-ms  5000)    ; after a kill, the least worth measuring: the teardown and the refaults take a moment
(def measure-ms 20000)   ; after a kill, how long the lagging average is no verdict


(defn init! [] (reset! last-kill* nil))


(defn victim
  "The app giving back the most memory, sparing VISIBLE unless it is alone."
  [apps visible]
  (let [others     (remove #(= visible (:app-id %)) apps)
        candidates (if (seq others) others apps)]
    (when (seq candidates)
      (apply max-key :bytes candidates))))


(defn stalled-since
  "Share (%) of the time since KILL with every task stalled on memory, from the PSI counter."
  [{:keys [at total]} now total-now]
  (let [ms (- now at)]
    (if (pos? ms) (/ (- total-now total) (* 10.0 ms)) 0.0)))


(defn thrashing?
  "The verdict on reading R at NOW: the 10 s average, except that it lags a KILL by its own
   length, so for measure-ms after one the verdict is the stall since it, nothing before settle-ms."
  [kill now {:keys [avg10 total]} limit]
  (let [since-kill (when kill (- now (:at kill)))]
    (cond (nil? since-kill)          (>= avg10 limit)
          (> since-kill measure-ms)  (>= avg10 limit)
          (< since-kill settle-ms)   false
          :else                      (>= (stalled-since kill now total) limit))))


(defn- kill! [{:keys [app-id dirs bytes]} r]
  (log/warn "memory pressure — killing an app" {:app app-id :mb (quot bytes 1048576) :full (:avg10 r)})
  (doseq [d dirs] (systemd/kill-cgroup! d))
  ;; the baseline is taken after the kill: the scope scan runs for seconds of stall under thrash
  (reset! last-kill* {:at (System/currentTimeMillis) :total (:total (or (memory/reading) r))}))


(defn relieve!
  "A pressure event is a trigger; the reading is taken now, an event can be seconds old under
   thrash. Not under the desktop's lock: the kill is one write that must not wait."
  [{:keys [limit]}]
  (let [now (System/currentTimeMillis)
        r   (memory/reading)]
    (when (and r (thrashing? @last-kill* now r limit))
      (let [on-screen (:id (:current (app/current-apps-state)))]
        (when-let [v (victim (memory/app-scopes) on-screen)]
          (kill! v r))))))
