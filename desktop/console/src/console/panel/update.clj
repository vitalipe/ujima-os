(ns console.panel.update
  "Update's slice of the console edge. This computer only — the pack is in its own port — so
   nothing takes targets and every job is local (console.upgrade drives ujimactl)."
  (:require [console.circle  :as circle]
            [console.jobs    :as jobs]
            [console.upgrade :as upgrade]))


(def ^:private app :update)


(defn- malformed! [message]
  (throw (ex-info message {:error :request/malformed})))


(defn- self-places
  "Where a stick's pack is found."
  []
  (let [me (circle/self)]
    (some (fn [p] (when (= me (:id p)) (:places p))) (circle/peers))))


(defn- slot-view [info slot]
  (let [rec (get-in info [:disk :slots slot :ujima-os])]
    {:slot         slot
     :version      (get-in rec [:image :version])
     :installed-at (:installed-at rec)
     :empty        (nil? rec)}))


(defn- disk-view [info]
  (let [running (:running-slot info)
        other   (if (= :a running) :b :a)
        armed   (get-in info [:disk :try-boot-slot])
        trial?  (boolean (:trial-boot? info))]
    {:trial   trial?
     :running (slot-view info running)
     ;; armed at the other slot while THIS one runs = a trial that fell back
     :other   (assoc (slot-view info other)
                     :failed-trial (boolean (and armed (= armed other) (not trial?))))}))


(defn view []
  (let [busy? (some? (jobs/active-action app))]
    (merge {:schema 1
            :self   (circle/self)
            :pack   (try (upgrade/offer (self-places)) (catch Throwable _ nil))
            :action (jobs/latest-action app)}
           (try (disk-view (upgrade/slots busy?))
                (catch Throwable e {:error (ex-message e)})))))


(defn- start! [verb make-flow body]
  (when (jobs/active-action app)
    (malformed! "an update is already running"))
  (let [{:keys [trial running other pack error]} (view)
        skip-trial (boolean (:skip-trial body))]
    (when error (malformed! error))
    (when trial (malformed! "a trial boot is running — keep it or go back in New version first"))
    (case verb
      :install (do (when-not pack (malformed! "no update on a stick"))
                   (when (= (:version pack) (:version running))
                     (malformed! "this version is already running")))
      :revert  (when (:empty other)
                 (malformed! "the other slot holds nothing to go back to")))
    (upgrade/stale-slots!)
    (jobs/run-local! app verb (make-flow pack skip-trial))))


(defn install-job! [body]
  (start! :install (fn [pack skip] (upgrade/install-flow (:path pack) skip)) body))


(defn revert-job! [body]
  (start! :revert (fn [_ skip] (upgrade/revert-flow skip)) body))
