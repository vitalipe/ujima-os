(ns ujima.linux.systemd
  "Per-app systemd --user scopes: the process-liveness + kill handle. The i3 tree owns identity
   and display; a scope only answers 'is this app's family alive?' and 'kill it.' Unit names are
   launch-unique (<id>-<millis>) so a relaunch never collides with a still-deactivating unit.
   A browser (chromium, Electron) moves its main process into a scope of its own,
   app-<name>-<pid>.scope, for the xdg portal; that pid is the one we spawned, so the stray
   scope is still the app's."
  (:require [babashka.fs        :as fs]
            [clojure.core.async :as async]
            [clojure.string     :as str]
            [lib.io             :as io]
            [lib.shell          :as shell]))


(def ^:private prefix      "ujima-app-")
(def ^:private cgroup-root "/sys/fs/cgroup")

(defonce ^:private spawned* (atom {}))   ; main pid -> the unit it was launched into

(defn- glob [id] (str prefix (name id) "-*.scope"))


(defn id-of-unit
  "The app-id in a scope unit name (ujima-app-<id>-<millis>.scope); nil if not ours."
  [s]
  (some->> s (re-find (re-pattern (str prefix "(.+)-\\d+\\.scope"))) second keyword))


(def ^:private app-oom-score "800")   ; the session tier runs at -900 (ujima.service + the user units)


(defn ram-mib
  "MemTotal in MiB — the per-app caps are fractions of it."
  []
  (-> (re-find #"MemTotal:\s+(\d+) kB" (io/slurp-text "/proc/meminfo" "")) second parse-long (quot 1024)))


(defn memory-accounting?
  "Is the memory controller on? Off (the Pi firmware default), MemoryMax is accepted and ignored."
  []
  (str/includes? (io/slurp-text "/sys/fs/cgroup/cgroup.controllers" "") "memory"))


(defn pid-of
  "The main pid of a spawned process (systemd-run, choom and setpriv exec in place); nil for none."
  [p]
  (some-> p :proc .pid str))


(defn spawn-scoped!
  "Launch EXEC (argv vector) into a fresh scope for ID; DIR is the process cwd, nil inherits.
   --no-new-privs: nothing in the scope, or anything it spawns, can regain privilege through
   setuid — so a spawned shell cannot sudo.

   The scope is a memory boundary: MemoryMax = half of RAM (a runaway app OOMs inside its own
   cgroup), MemorySwapMax = a quarter (swapped pages escape MemoryMax), OOMPolicy=kill (one
   OOM-killed process takes the whole app, no half-dead window). choom puts the app at +800 — a
   scope has no exec context for OOMScoreAdjust= — against the session tier's -900. Chromium
   resets its own to 0, so web apps die last among apps: wanted, a privileged floor would undo it.

   --scope blocks, so never deref this. And the flag is set by setpriv in the forked process
   because a scope has no NoNewPrivileges= property — systemd accepts one and ignores it.

   --expand-environment=no keeps EXEC literal: app.edn is external data, and systemd warns it
   will start expanding $VAR in scope command lines in a future release.

   OPTS are spawn options, merged over the defaults:

     (spawn-scoped! :console [\"ujima-console\"] \"/ujima/apps/console\"
                    {:extra-env {\"UJIMA_CIRCLE_TOKEN\" token}})

   `:privileged?` is OURS, stripped before the spawn: it drops the no-new-privs prefix, the
   only way anything in a scope reaches `sudo`. Who gets it is app.act's call, never an app.edn.

   Keep secrets out of EXEC: systemd-run copies the command line into the scope's
   Description, and systemd logs that to the journal."
  [id exec dir opts]
  (let [privileged? (:privileged? opts)
        ram         (ram-mib)
        unit        (str prefix (name id) "-" (System/currentTimeMillis) ".scope")
        p           (apply shell/sh (merge {:out :inherit :err :inherit :dir dir} (dissoc opts :privileged?))
           :systemd-run :--user :--scope :--collect (str "--unit=" unit)
           "--property=TimeoutStopSec=3"
           "--property=OOMPolicy=kill"
           (str "--property=MemoryMax=" (quot ram 2) "M")
           (str "--property=MemorySwapMax=" (quot ram 4) "M")
           "--expand-environment=no"
           "--" :choom :-n app-oom-score "--"
           (if privileged?
             (into [] exec)
             (into [:setpriv :--no-new-privs] exec)))]
    (when-let [pid (pid-of p)] (swap! spawned* assoc pid unit))
    p))


(defn- live-units [pattern]
  (let [{:keys [ok? out]} (shell/sh? :systemctl :--user "list-units" "--plain" "--no-legend"
                                     "--state=active" pattern)]
    (when ok?
      (keep #(some-> (re-find #"^(ujima-app-\S+\.scope)" (str/trim %)) second)
            (str/split-lines (or out ""))))))


(defn active?
  "Sync: does ID have a live scope? The launch gate + the open-url cold/warm test."
  [id]
  (boolean (seq (live-units (glob id)))))


(defn- app-slice-dir
  "The user manager's app.slice, from our own cgroup."
  []
  (when-let [uid (second (re-find #"/user-(\d+)\.slice" (io/slurp-text "/proc/self/cgroup" "")))]
    (str cgroup-root "/user.slice/user-" uid ".slice/user@" uid ".service/app.slice")))


(defn- unit-of-dir
  "DIR's ujima scope: its own name, or the one the pid in a browser's app-*-<pid>.scope was spawned into."
  [dir]
  (let [n (fs/file-name dir)]
    (if (id-of-unit n)
      n
      (when-let [[_ pid] (re-find #"^app-.+-(\d+)\.scope$" n)]
        (get @spawned* pid)))))


(defn app-cgroups
  "Every cgroup under the app slice that is an app's: {:unit :app-id :dir}."
  ([] (some-> (app-slice-dir) app-cgroups))
  ([slice]
   (when (fs/directory? slice)
     (vec (for [d (fs/list-dir slice) :when (fs/directory? d) :let [u (unit-of-dir d)] :when u]
            {:unit u :app-id (id-of-unit u) :dir (str d)})))))


(defn kill-cgroup!
  "SIGKILL the cgroup at DIR, by the kernel, no fork. A cgroup that is already gone is done;
   a knob that is there and cannot be written is still an error."
  [dir]
  (let [knob (str dir "/cgroup.kill")]
    (try (spit knob "1")
         (catch java.io.FileNotFoundException e
           (when (fs/exists? knob) (throw e))))))


(defn- strays
  "[unit dir] of the scopes browsers made out of ID's; ORPHANS-ONLY? = those whose own scope is gone."
  [id orphans-only?]
  (let [live (set (live-units (glob id)))]
    (for [{:keys [unit dir]} (app-cgroups)
          :when (= id (id-of-unit unit))
          :when (not= (fs/file-name dir) unit)
          :when (or (not orphans-only?) (not (live unit)))]
      [unit dir])))


(defn- forget! [units]
  (let [gone? (set units)]
    (swap! spawned* (fn [m] (into {} (remove #(gone? (val %)) m))))))


(defn- kill-strays!
  "SIGKILL: the browser's scope has the 90 s default stop timeout, and an Electron main with no
   family ignores SIGTERM."
  [pairs]
  (doseq [[_ dir] pairs] (kill-cgroup! dir))
  (forget! (map first pairs)))


(defn stop!
  "Force-kill ID's scope and whatever a browser moved out of it. Missing is fine."
  [id]
  (let [units (live-units (glob id))]
    (doseq [u units] (shell/sh? :systemctl :--user "stop" u))
    (kill-strays! (strays id false))
    (forget! units)))


(defn reap!
  "ID's scope died: finish a browser main process living on in its own scope."
  [id]
  (kill-strays! (strays id true)))


(defn- live-app-ids [] (into #{} (keep id-of-unit) (live-units (str prefix "*.scope"))))


(defn watch-scopes!
  "Poll live scopes; emit {:type :scope/died :app-id X} for each that vanished. Fixed
   buffer: every death matters."
  [{:keys [interval-ms] :or {interval-ms 1000}}]
  (let [ch (async/chan 64)]
    (async/thread
      (loop [prev #{}]
        (let [cur (try (live-app-ids) (catch Throwable _ prev))]
          (doseq [id prev :when (not (contains? cur id))]
            (async/>!! ch {:type :scope/died :app-id id}))
          (Thread/sleep (long interval-ms))
          (recur cur))))
    ch))
