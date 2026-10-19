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


(defn parse-full
  "PSI TEXT's full line: {:avg10 share (%) of the last 10 s with every task stalled on memory,
   :total such microseconds since boot}; nil without PSI."
  [text]
  (when-let [[_ avg total] (re-find #"full avg10=([\d.]+).*total=(\d+)" text)]
    {:avg10 (parse-double avg) :total (parse-long total)}))


(defn reading []
  (parse-full (io/slurp-text psi-path "")))


(defn- counter [path]
  (or (some-> (io/slurp-text path nil) str/trim parse-long) 0))


(defn- anon [dir]
  (or (some-> (re-find #"(?m)^anon (\d+)" (io/slurp-text (str dir "/memory.stat") "")) second parse-long) 0))


(defn- reclaimable [dir]
  (+ (anon dir) (counter (str dir "/memory.swap.current"))))


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
  "Emit {:type :memory/pressure :full x :limit LIMIT} every INTERVAL-MS while full avg10 is at
   or over LIMIT. Sliding buffer: only the latest reading matters."
  [{:keys [interval-ms limit] :or {interval-ms 1000 limit 15.0}}]
  (let [ch (async/chan (async/sliding-buffer 1))]
    (async/thread
      (loop []
        (let [{:keys [avg10]} (reading)]
          (when (and avg10 (>= avg10 limit))
            (async/>!! ch {:type :memory/pressure :full avg10 :limit limit})))
        (Thread/sleep (long interval-ms))
        (recur)))
    ch))
