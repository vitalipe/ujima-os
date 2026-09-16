(ns console.panel.tryboot
  "The trial-boot page's slice: the two versions in play, and the one decision — keep this
   slot, or restart and let the firmware fall back."
  (:require [console.circle  :as circle]
            [console.jobs    :as jobs]
            [console.upgrade :as upgrade]))


(def ^:private app :tryboot)


(defn- malformed! [message]
  (throw (ex-info message {:error :request/malformed})))


(defn- slot-view [info slot]
  {:slot slot :version (get-in info [:disk :slots slot :ujima-os :image :version])})


(defn view []
  (let [busy? (some? (jobs/active-action app))]
    (merge {:schema 1
            :self   (circle/self)
            :action (jobs/latest-action app)}
           (try
             (let [info (upgrade/slots busy?)]
               {:trial    (boolean (:trial-boot? info))
                :running  (slot-view info (:running-slot info))
                :previous (slot-view info (get-in info [:disk :boot-slot]))})
             (catch Throwable e {:error (ex-message e)})))))


(defn keep-job! []
  (when (jobs/active-action app)
    (malformed! "already deciding"))
  (let [{:keys [trial error]} (view)]
    (when error (malformed! error))
    (when-not trial (malformed! "this is not a trial boot — nothing to keep"))
    (jobs/run-local! app :keep (upgrade/keep-flow))))


(defn back-job! []
  (when (jobs/active-action app)
    (malformed! "already deciding"))
  (jobs/run-local! app :back (upgrade/back-flow)))
