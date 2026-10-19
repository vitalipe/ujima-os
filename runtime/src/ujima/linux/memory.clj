(ns ujima.linux.memory
  "Memory pressure and what a kill gives back. PSI (psi=1 on the cmdline) says how long every
   task stalled on memory; the app cgroups say what a kill frees."
  (:require [babashka.fs         :as fs]
            [clojure.core.async  :as async]
            [clojure.string      :as str]
            [lib.io              :as io]
            [ujima.linux.systemd :as systemd]))


(def ^:private psi-path "/proc/pressure/memory")


(defn psi? [] (fs/exists? psi-path))


(defn full-avg10
  "Share (%) of the last 10 s in which every task stalled on memory; nil without PSI."
  [text]
  (some-> (re-find #"full avg10=([\d.]+)" text) second parse-double))


(defn- counter [path] (or (some-> (io/slurp-text path nil) str/trim parse-long) 0))

(defn- anon [dir]
  (or (some-> (re-find #"(?m)^anon (\d+)" (io/slurp-text (str dir "/memory.stat") "")) second parse-long) 0))

(defn- reclaimable [dir] (+ (anon dir) (counter (str dir "/memory.swap.current"))))


(defn app-scopes
  "Live apps with what a kill gives back: anon + swapped bytes over every cgroup of the app
   (CGROUPS as systemd/app-cgroups lists them). File cache is nobody's."
  ([] (app-scopes (systemd/app-cgroups)))
  ([cgroups]
   (vec (for [[unit gs] (group-by :unit cgroups)
              :let [dirs (mapv :dir gs)]]
          {:app-id (systemd/id-of-unit unit)
           :dirs   dirs
           :bytes  (reduce + (map reclaimable dirs))}))))


(defn watch-pressure!
  "Emit {:type :memory/pressure :full x} every INTERVAL-MS while full avg10 is at or over
   LIMIT. Sliding buffer: only the latest reading matters."
  [{:keys [interval-ms limit] :or {interval-ms 1000 limit 15.0}}]
  (let [ch (async/chan (async/sliding-buffer 1))]
    (async/thread
      (loop []
        (let [full (full-avg10 (io/slurp-text psi-path ""))]
          (when (and full (>= full limit))
            (async/>!! ch {:type :memory/pressure :full full})))
        (Thread/sleep (long interval-ms))
        (recur)))
    ch))
