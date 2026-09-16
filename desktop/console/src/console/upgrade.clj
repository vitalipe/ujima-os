(ns console.upgrade
  "This machine's slots: every privileged step is a `sudo ujimactl upgrade …` child. The
   console's scope is launched without no-new-privs (ujima.desktop.app.act), so this backend
   can elevate — and only it, since run.sh puts the browser back under the flag.

   Reading the disk mounts partitions, so that view is cached and never read while a job may
   be writing a slot."
  (:require [clojure.edn     :as edn]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [babashka.fs     :as fs]
            [cheshire.core   :as json]
            [lib.shell       :refer [sh sh?]]
            [lib.task.flow   :refer [flow <step!]]
            [schema.ujima.storage :as stick]
            [ujima.pack      :as pack]))


(def ^:private linger-ms  5000)    ;; the last message stays readable before the box goes down
(def ^:private slots-ttl-ms 60000)


;; ── talking to ujimactl ─────────────────────────────────────────────────────

(defn- edn-line
  "The one line of OUT that reads as EDN — the runtime logs to the same stdout."
  [out what]
  (or (->> (str/split-lines (str out))
           (keep (fn [line]
                   (when (re-find #"^\s*[\{\[]" line)
                     (try (edn/read-string line) (catch Throwable _ nil)))))
           (last))
      (throw (ex-info (str "could not read " what " from ujimactl") {:output (str out)}))))


(defn- ctl!
  "One-shot; its stdout, or a throw worded by its stderr."
  [what & args]
  (let [{:keys [ok? out err]} (apply sh? :sudo "-n" "ujimactl" args)]
    (when-not ok?
      (throw (ex-info (str what " failed: " (str/trim (str err out))) {:args (vec args)})))
    out))


(defn- stream-ctl!
  "A `--events` verb: each EDN line reaches ON-EVENT as it arrives, and the read blocks until
   the child is done. A non-zero exit throws, worded by its :error event or its stderr."
  [on-event & args]
  (let [proc   (apply sh {:err :string} :sudo "-n" "ujimactl" args)
        error* (atom nil)]
    (with-open [r (io/reader (:out proc))]
      (doseq [line (line-seq r)]
        (when-let [evt (try (edn/read-string line) (catch Throwable _ nil))]
          (when (map? evt)
            (when (= :error (:type evt)) (reset! error* (:message evt)))
            (on-event evt)))))
    (let [{:keys [exit err]} @proc]
      (when-not (zero? exit)
        (throw (ex-info (or @error* (str "ujimactl " (str/join " " args) " failed: "
                                         (str/trim (str err))))
                        {:exit exit}))))))


;; ── the disk's view, cached ─────────────────────────────────────────────────

(defonce ^:private slots* (atom nil))   ;; {:at ms :info {...}}


(defn info
  "The running slot, the trial flag, and both slots' records."
  []
  (edn-line (ctl! "reading the A/B status" "upgrade" "info") "the A/B status"))


(defn slots
  "The cached disk view. BUSY? pins it — a stale answer beats mounting a partition under a
   running dd."
  [busy?]
  (let [{:keys [at] :as held} @slots*
        fresh? (and at (< (- (System/currentTimeMillis) at) slots-ttl-ms))]
    (if (or fresh? (and busy? held))
      (:info held)
      (let [i (info)]
        (reset! slots* {:at (System/currentTimeMillis) :info i})
        i))))


(defn stale-slots!
  "The next idle read goes to the disk; the busy ones still get the cache."
  []
  (swap! slots* #(some-> % (assoc :at 0))))


;; ── the pack a stick offers ─────────────────────────────────────────────────

(defonce ^:private manifests* (atom {}))   ;; [path mtime size] -> manifest


(defn- manifest [path]
  (let [k [path (str (fs/last-modified-time path)) (fs/size path)]]
    (or (get @manifests* k)
        (when-let [mf (pack/manifest path)]
          (reset! manifests* {k mf})
          mf))))


(defn- registration
  "The stick's ujima/install.json — the machine validated its shape at the sweep."
  [mount]
  (let [f (fs/path mount stick/dir "install.json")]
    (when (fs/regular-file? f)
      (let [{:keys [pack label]} (try (json/parse-string (slurp (str f)) true)
                                      (catch Throwable _ nil))]
        (when (and (string? pack) (not (str/blank? pack)))
          {:pack pack :label label})))))


(defn- resolve-pack
  "Against the place's own root: relative only, never climbing out."
  [mount {:keys [pack label]}]
  (when-not (or (str/starts-with? pack "/") (some #{".."} (str/split pack #"/")))
    (let [path (str (fs/path mount pack))]
      (when (fs/regular-file? path)
        {:path path :label label}))))


(defn- pack-facts [{:keys [path label]} stick-label]
  (when-let [mf (manifest path)]
    {:path      path
     :label     label
     :stick     stick-label
     :version   (get-in mf [:image :version])
     :packed-at (:packed-at mf)
     :bytes     (fs/size path)}))


(defn offer
  "The first ready place registering a pack that resolves, nil when none does. PLACES is the
   machine tree's blob, so label and token shape were settled before it got here."
  [places]
  (some (fn [{:keys [state tokens mount label]}]
          (when (and (= :ready state) mount (some #{"ujima/pack"} tokens))
            (some-> (registration mount)
                    (->> (resolve-pack mount))
                    (pack-facts label))))
        places))


;; ── the steps ───────────────────────────────────────────────────────────────

(defn- export
  "The migration export, not the API's public settings: the psk is what makes the new slot
   reachable."
  []
  (let [{:keys [ok? out err]} (sh? :ujimactl "migration" "export")]
    (when-not ok?
      (throw (ex-info (str "settings export failed: " (str/trim (str err out))) {})))
    (edn-line out "the settings export")))


(defn- migrate!
  "ENTRIES into the other slot through ITS own runtime; the report names what it dropped."
  [entries]
  (let [{:keys [ok? out err]} (sh? {:in (pr-str entries)}
                                   :sudo "-n" "ujimactl" "upgrade" "migrate")]
    (when-not ok?
      (throw (ex-info (str "carrying the settings failed: " (str/trim (str err out))) {})))
    (edn-line out "the migration report")))


(defn- leave!
  "Reboot into the other slot: a trial the machine must confirm, or for good."
  [skip-trial?]
  (ctl! (if skip-trial? "activating the slot" "arming the trial boot")
        "upgrade" (if skip-trial? "activate" "boot")))


(defn- dropped-line [dropped]
  (when (seq dropped)
    (str ", dropped " (count dropped) ": "
         (str/join ", " (map #(pr-str (get-in % [:entry :setting])) dropped)))))


;; ── the flows a job runs ────────────────────────────────────────────────────

(defn install-flow
  "PACK into the other slot, the settings after it, then the reboot — a trial, or onto the
   slot when SKIP-TRIAL?. The report lingers so a dropped entry is seen at least once."
  [pack skip-trial?]
  (flow :install
    (<step! 85 :write
      (progress! 0 "starting the install")
      (stream-ctl! (fn [{:keys [type progress message]}]
                     (when (= :progress type) (progress! progress message)))
                   "upgrade" "install" pack "--events"))

    (<step! 95 :migrate
      (progress! 0 "carrying this computer's settings over")
      (let [entries (export)]
        (if (empty? entries)
          (progress! 100 "nothing set on this computer to carry over")
          (let [{:keys [applied dropped]} (migrate! entries)]
            (progress! 100 (str "carried " applied " settings" (dropped-line dropped)))))))

    (progress! 97 (if skip-trial?
                    "restarting onto the new version"
                    "restarting to try the new version"))
    (Thread/sleep linger-ms)
    (<step! 100 :leave (leave! skip-trial?))
    {:pack pack :trial (not skip-trial?)}))


(defn revert-flow
  "Into the other slot as it stands — a trial, or for good when SKIP-TRIAL?."
  [skip-trial?]
  (flow :revert
    (progress! 20 (if skip-trial?
                    "restarting onto the previous version"
                    "restarting to try the previous version"))
    (Thread/sleep (quot linger-ms 2))
    (<step! 100 :leave (leave! skip-trial?))
    {:trial (not skip-trial?)}))


(defn keep-flow
  "Commit the slot this trial boot is running."
  []
  (flow :keep
    (progress! 20 "keeping this version")
    (<step! 100 :commit (ctl! "committing the slot" "upgrade" "commit"))
    (stale-slots!)
    {:kept true}))


(defn back-flow
  "A plain restart — an uncommitted trial falls back on its own."
  []
  (flow :back
    (progress! 20 "restarting on the previous version")
    (Thread/sleep (quot linger-ms 2))
    (<step! 100 :reboot
      (let [{:keys [ok? err]} (sh? :sudo "-n" "systemctl" "reboot")]
        (when-not ok? (throw (ex-info (str "restart failed: " (str/trim (str err))) {})))))
    {}))
