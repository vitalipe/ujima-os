(ns ujima.desktop.http.converge
  "The GUI's converge ports and the streams they feed. control hands it the
   whole settings plane, so this projects; the app layer projects before its
   targets run, so that one republishes as-is."
  (:require [lib.http.ndjson        :as ndjson]
            [ujima.circle           :as circle]
            [ujima.circle.self      :as self]
            [ujima.control.queries  :as queries]
            [ujima.desktop.places   :as places]
            [ujima.linux.devicetree :as devicetree]
            [ujima.storage          :as storage]))

(def pinned-app-order {:files   0   ; the one a user reaches for — it must not move
                       :console 1})

;; machine identity is static — one devicetree read (nil off-Pi: x86 dev hosts)
(def ^:private serial-tail (delay (devicetree/serial-tail)))

(defn- lockable [record]
  {:value  (:effective record)
   :locked (= :activity (:via record))})


(defn settings->ui
  "Settings records (+ the static serial tail) -> the UI blob."
  [settings serial]
  (let [output (:effective (get settings [:audio :active]))]

    {:system   {:name        (:effective (get settings [:system :name]))
                :serial-tail serial}

     :audio    {:volume (lockable (get settings [:audio output :volume]))
                :muted  (lockable (get settings [:audio :muted]))
                :output (lockable (get settings [:audio :active]))}

     :keyboard {:layout  (lockable (get settings [:keyboard :layout]))
                :layouts (queries/ordered-layouts settings)
                :next    (queries/next-keyboard-layout settings)}}))


(defn apps->ui
  "The app snapshot -> the shell's blob. This is the wire contract."
  [{:keys [running catalog current mode bars-hidden?]}]
  {:mode        mode
   :bars-hidden (boolean bars-hidden?)
   :running     running
   :current     current
   :pinned      (->> catalog
                     (remove :hidden)
                     (filter (fn [{category :category}] (= category :system)))
                     (map #(select-keys % [:id :label :icon :category :description]))
                     (sort-by #(pinned-app-order (:id %) 99)))})


(defn- publish-places! []
  (ndjson/publish! :ui/places (places/places->ui (storage/snapshot) (:shares (self/snapshot)) (circle/nearby))))

(defn converge-ui!     [settings _prv] (ndjson/publish! :ui/state (settings->ui settings @serial-tail)))
(defn converge-apps!   [snapshot _prv] (ndjson/publish! :ui/apps  (apps->ui snapshot)))
;; the places blob is storage x self's shares x the circle's nearby; self hears storage, so
;; self's arrow and the circle's carry all three
(defn converge-places! [_nxt _prv] (publish-places!))

(defn stream-ui     [req] (ndjson/subscribe! :ui/state  req))
(defn stream-apps   [req] (ndjson/subscribe! :ui/apps   req))
(defn stream-places [req] (ndjson/subscribe! :ui/places req))


(defn init!
  "Declare the topics before anything serves or converges."
  []
  (ndjson/topic! :ui/state)
  (ndjson/topic! :ui/apps   {:running [] :pinned [] :current nil})
  (ndjson/topic! :ui/places {:places []}))
