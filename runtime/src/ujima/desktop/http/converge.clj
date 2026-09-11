(ns ujima.desktop.http.converge
  "The GUI's converge ports and the streams they feed. control hands it the
   whole settings plane, so this projects; the app layer projects before its
   targets run, so that one republishes as-is."
  (:require [lib.http.ndjson        :as ndjson]
            [ujima.control.queries  :as queries]
            [ujima.linux.devicetree :as devicetree]))

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
  [{:keys [running catalog current mode]}]
  {:mode    mode
   :running running
   :current current
   :pinned  (->> catalog
             (remove :hidden)
             (filter (fn [{category :category}] (= category :system)))
             (map #(select-keys % [:id :label :icon]))
             (sort-by #(pinned-app-order (:id %) 99)))})


;; --- places: the file model's projection ------------------------------------
;; The :storage-provision view — one browse root per place. :apps and :tokens
;; reach the catalog and token policy, never a picker.

(defn- entry->place [{:keys [kind name state storage label fstype tokens reason]}]
  (let [state ({:mounted :ready :detected :mounting} state state)]
    (cond-> {:id [kind name] :kind kind :state state :label label :fstype fstype}
      (= :ready   state) (assoc :storage storage
                                :tokens  (->> (keys tokens) (map #(str (symbol %))) sort vec))  ; types only
      (= :invalid state) (assoc :reason reason))))


(defn places->ui
  "Storage entries -> the places blob. The wire contract."
  [entries]
  {:places (mapv entry->place entries)})


(defn converge-ui!     [settings _prv] (ndjson/publish! :ui/state (settings->ui settings @serial-tail)))
(defn converge-apps!   [snapshot _prv] (ndjson/publish! :ui/apps  (apps->ui snapshot)))
(defn converge-places! [entries  _prv] (ndjson/publish! :ui/places (places->ui entries)))

(defn stream-ui     [req] (ndjson/subscribe! :ui/state  req))
(defn stream-apps   [req] (ndjson/subscribe! :ui/apps   req))
(defn stream-places [req] (ndjson/subscribe! :ui/places req))


(defn init!
  "Declare the topics before anything serves or converges."
  []
  (ndjson/topic! :ui/state)
  (ndjson/topic! :ui/apps   {:running [] :pinned [] :current nil})
  (ndjson/topic! :ui/places {:places []}))
