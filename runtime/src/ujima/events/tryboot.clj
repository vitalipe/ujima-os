(ns ujima.events.tryboot
  "Trial-boot policy: woken in a slot the disk has not committed to, the tryboot app opens
   first and the Console keeps its dock pin but never the screen (events/token). The fact is
   read once per session, never remembered across one."
  (:require [ujima.log :as log]
            [ujima.control :as control]
            [ujima.desktop.app :as app]
            [ujima.desktop.app.catalog :as catalog]
            [ujima.device.ab :as ab]))


(def ^:private tryboot :tryboot)

;; the slot the token stick fills for the Console; here it is the machine's own key
(def token-env "UJIMA_CIRCLE_TOKEN")

(defonce ^:private trial?* (atom false))


(defn init!
  "nil BOOT-RT (a dev host) is never a trial."
  [boot-rt]
  (reset! trial?* (boolean (when boot-rt (ab/trial-boot? boot-rt)))))


(defn trial? [] @trial?*)


(defn open!
  "Unhide and run the tryboot app on a trial boot. A catalog without it is an error line,
   never a dead boot."
  []
  (when (trial?)
    (try
      (catalog/merge-app! tryboot {:hidden false
                                   :env {token-env (:effective (control/setting [:circle :token]))}})
      (log/info "trial boot — opening the tryboot app")
      (app/run! tryboot)
      (catch Throwable e
        (log/error "trial boot, but the tryboot app could not open" {:error (ex-message e)})))))
