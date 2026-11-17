(ns ujima.desktop.places
  "Places, presented. Projections of the storage plane's entries, from ONE source of names:
   the wire blob that /stream/places carries (the Files app's cards); the user's home — the
   Places screen IS home: one entry per ready place, named as its card, so a dialog opened at ~
   shows the same places the Files app does and GTK's crumbs collapse behind the home button; and
   the by-id root, every mounted place under <kind>/<id>, one fixed path per place while it is
   mounted. Each is a directory of symlinks reconciled on the storage arrow
   (ujima.desktop.places.links)."
  (:require [babashka.fs                :as fs]
            [ujima.desktop.places.links :as links]
            [ujima.log                  :as log]))


;; --- the wire blob: what /stream/places carries ------------------------------
;; The :storage-provision view — one browse root per place. :apps and :tokens
;; reach the catalog and token policy, never a picker.

(def ^:private kind->name
  "What a place is called: the card's title in the Files app AND its folder in every dialog
   (ujima.desktop.home) — one source, so the two can never disagree."
  {:session "Temporary" :local "This Computer" :usb "USB Stick" :peer "Nearby Computer"})


(defn- entry->place [{:keys [kind state storage mount label fstype tokens reason] :as entry}]
  (let [state ({:mounted :ready :detected :mounting} state state)]
    (cond-> {:id    [kind (:name entry)]
             :kind  kind
             :name  (kind->name kind (name kind))
             :state state :label label :fstype fstype}
      (= :ready   state) (assoc :storage storage
                                :mount   mount
                                :tokens  (->> (keys tokens) (map #(str (symbol %))) sort vec))  ; types only
      (= :invalid state) (assoc :reason reason))))


(defn- number-sticks
  "Two sticks or more and every stick is numbered — \"USB Stick\", \"USB Stick 2\" — in label
   then id order, so two identical sticks stay apart as folders. One stick keeps the bare name."
  [places]
  (let [sticks (->> places (filter #(= :usb (:kind %))) (sort-by (juxt #(or (:label %) "") :id)))
        nth-of (zipmap (map :id sticks) (iterate inc 1))]
    (if (< (count sticks) 2)
      places
      (mapv (fn [{:keys [id kind] :as place}]
              (let [n (nth-of id)]
                (cond-> place (and (= :usb kind) (> n 1)) (update :name str " " n))))
            places))))


(defn places->ui
  "Storage entries -> the places blob. The wire contract; :name is the display name."
  [entries]
  {:places (number-sticks (mapv entry->place entries))})


;; --- home: the Places screen on disk -----------------------------------------

(defonce ^:private cfg* (atom {}))
(def ^:private default-places-dir "/ujima/run/places")

(defn init!
  "PLACES-HOME is the dir that plays the Places screen, PLACES-DIR the by-id root; nil = off."
  [{:keys [places-home places-dir]}]
  (reset! cfg* {:home places-home :places-dir places-dir}))


(defn wanted
  "The places blob -> {name storage-root} for the places that are ready to browse."
  [places]
  (into {} (for [{:keys [state name storage]} places
                 :when (and (= :ready state) storage)]
             [name (str (fs/path storage))])))


;; --- the by-id root: every mounted place, by id -------------------------------

(defn place-path
  "[:usb \"6962-5E15\"] -> \"/ujima/run/places/usb/6962-5E15\": the place's storage root, by id,
   while it is mounted."
  [[kind id]]
  (str (fs/path (or (:places-dir @cfg*) default-places-dir) (name kind) id)))


(defn- by-id
  "Storage entries -> {kind {id storage-root}}, the mounted ones."
  [entries]
  (into {} (for [[kind entries] (group-by :kind entries)]
             [(name kind) (into {} (for [{id :name :keys [state storage]} entries
                                         :when (and (= :mounted state) storage)]
                                     [id (str (fs/path storage))]))])))


(defn- publish-by-id!
  "PLACES-DIR holds, per kind, a link per mounted entry to its storage root."
  [places-dir entries]
  (let [wanted  (by-id entries)
        on-disk (when (fs/directory? places-dir) (map fs/file-name (fs/list-dir places-dir)))]
    (doseq [kind (into #{} (concat (keys wanted) on-disk))]
      (links/reconcile! (fs/path places-dir kind) (get wanted kind {})))))


;; --- the storage arrow --------------------------------------------------------

(defn converge!
  "The storage arrow: entries -> the by-id root and home. Fails loud in the log, never into
   the plane."
  [entries _prv]
  (let [{:keys [places-dir home]} @cfg*]
    (when places-dir
      (try (publish-by-id! places-dir entries)
           (catch Throwable e
             (log/error "places: by-id root failed" {:root places-dir :error (ex-message e)}))))
    (when home
      (try (links/reconcile! home (wanted (:places (places->ui entries))))
           (catch Throwable e
             (log/error "places: home projection failed" {:root home :error (ex-message e)}))))))

