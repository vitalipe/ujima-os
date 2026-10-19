(ns ujima.linux.memory
  "Memory pressure and what a kill gives back. PSI (psi=1 on the cmdline) says how long every
   task stalled on memory; the app scopes say what a kill frees; cgroup.kill is the kill handle,
   one write and no fork, so it works while the machine thrashes."
  (:require [babashka.fs         :as fs]
            [clojure.core.async  :as async]
            [clojure.string      :as str]
            [lib.io              :as io]
            [ujima.linux.systemd :as systemd]))


(def ^:private psi-path    "/proc/pressure/memory")
(def ^:private cgroup-root "/sys/fs/cgroup")


(defn psi? [] (fs/exists? psi-path))


(defn full-avg10
  "Share (%) of the last 10 s in which every task stalled on memory; nil without PSI."
  [text]
  (some-> (re-find #"full avg10=([\d.]+)" text) second parse-double))


(defn- app-slice
  "The user manager's app.slice, from our own cgroup."
  []
  (when-let [uid (second (re-find #"/user-(\d+)\.slice" (io/slurp-text "/proc/self/cgroup" "")))]
    (str cgroup-root "/user.slice/user-" uid ".slice/user@" uid ".service/app.slice")))


(defn- counter [path] (or (some-> (io/slurp-text path nil) str/trim parse-long) 0))

(defn- anon [dir]
  (or (some-> (re-find #"(?m)^anon (\d+)" (io/slurp-text (str dir "/memory.stat") "")) second parse-long) 0))


(defn app-scopes
  "Live app scopes under DIR with what a kill gives back: anon + swapped bytes. File cache is
   nobody's."
  ([] (some-> (app-slice) app-scopes))
  ([dir]
   (when (fs/directory? dir)
     (vec (for [d    (fs/list-dir dir)
                :let [id (systemd/id-of-unit (fs/file-name d))]
                :when id]
            {:app-id id :dir (str d) :bytes (+ (anon d) (counter (str d "/memory.swap.current")))})))))


(defn kill-scope!
  "SIGKILL the scope at DIR, by the kernel, no fork."
  [dir]
  (spit (str dir "/cgroup.kill") "1"))


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
