(ns ujima.desktop.places
  "Places, presented: the blob /stream/places carries, and three directories of links, each
   reconciled on its arrow by ujima.desktop.places.links:
     ~/USB Stick                      -> /ujima/run/storage/6962-5E15                 home = the Places screen
     /ujima/run/places/usb/6962-5E15  -> /ujima/run/storage/6962-5E15                 every mounted place, by id
     /ujima/run/shares/rw/math        -> /ujima/run/places/usb/6962-5E15/Grade 5/math  the Samba roots
   A share a peer offers is a place too, joined from the circle's nearby list and storage's
   mount of that peer's root; `mount!` asks storage for the root behind one."
  (:require [babashka.fs                :as fs]
            [clojure.string             :as str]
            [lib.util                   :refer [find-first]]
            [ujima.circle               :as circle]
            [ujima.desktop.places.links :as links]
            [ujima.linux.smb            :as smb]
            [ujima.log                  :as log]
            [ujima.storage              :as storage]))


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


(defn- peer-mount?
  "A peer's root mounted is an entry, not a place: its shares are the places."
  [entry]
  (= :peer (:kind entry)))


(defn- peer-place
  "A share a peer offers, as a place: :available until its root is asked for, then that
   mount's state, the browse root the share's folder under it."
  [mounts {:keys [peer-id host label access]}]
  (let [{:keys [state storage reason]} (mounts [peer-id access])
        state ({:mounted :ready :detected :mounting} state state)]
    (cond-> {:id    [:peer (str peer-id "/" label)]
             :kind  :peer
             :name  (kind->name :peer)
             :label label :host host :host-id peer-id :access access
             :state (or state :available)}
      (= :ready   state) (assoc :storage (str (fs/path storage label)))
      (= :invalid state) (assoc :reason reason))))

(defn- peer-places
  "NEARBY's shares as places, each at the state of storage's mount of its peer and access."
  [entries nearby]
  (let [mounts (into {} (for [{:keys [peer access] :as entry} (filter peer-mount? entries)]
                          [[peer access] entry]))]
    (mapv (partial peer-place mounts) nearby)))


(defn places->ui
  "Entries, shares, nearby -> the wire blob: each place with its own shares as
   [{:name \"math\" :path \"Grade 5/math\" :access :rw}], then a place per share nearby."
  ([entries] (places->ui entries [] []))
  ([entries shares] (places->ui entries shares []))
  ([entries shares nearby]
   (let [by-place (group-by :place shares)
         own      (number-sticks (mapv entry->place (remove peer-mount? entries)))]
     {:places (mapv (fn [{:keys [id] :as place}]
                      (assoc place :shares (mapv #(select-keys % [:name :path :access]) (by-place id))))
                    (into own (peer-places entries nearby)))})))


;; --- home: the Places screen on disk -----------------------------------------

(defonce ^:private cfg* (atom {}))
(def ^:private default-places-dir "/ujima/run/places")

(defn init!
  "PLACES-HOME is the dir that plays the Places screen, PLACES-DIR the by-id root; nil = off."
  [{:keys [places-home places-dir]}]
  (reset! cfg* {:home places-home :places-dir places-dir}))


(defn wanted
  "The places blob -> {name storage-root} for the places that are ready to browse. A peer's
   share is not one of them: every one is called Nearby Computer."
  [places]
  (into {} (for [{:keys [kind state name storage]} places
                 :when (and (not= :peer kind) (= :ready state) storage)]
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
  (into {} (for [[kind entries] (group-by :kind (remove peer-mount? entries))]
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


;; --- the two arrows -----------------------------------------------------------

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


(defn converge-shares!
  "The self arrow: /ujima/run/shares/rw/math -> /ujima/run/places/usb/6962-5E15/Grade 5/math,
   dangling while the stick is out."
  [{:keys [shares]} _prv]
  (doseq [[access root] smb/roots]
    (links/reconcile! root (into {} (for [{:keys [place path name] :as share} shares
                                          :when (= access (:access share))]
                                      [name (str (fs/path (place-path place) path))])))))


;; --- mounting a peer's share -----------------------------------------------------

(defn- offers? [peer label {:keys [peer-id] :as share}]
  (and (= peer peer-id) (= label (:label share))))

(defn peer-request
  "[:peer \"87f6…/math\"] against NEARBY -> what storage mounts for it, {:peer :access :host};
   a place no present peer offers throws :places/unknown-place."
  [nearby [_kind name]]
  (let [[peer label] (str/split name #"/" 2)
        share        (find-first (partial offers? peer label) nearby)]
    (when-not share
      (throw (ex-info "no such place nearby" {:error :places/unknown-place :place [:peer name]})))
    {:peer peer :access (:access share) :host (get-in share [:address :host])}))

(defn mount!
  "Ask storage for the root behind PLACE, a peer's share; the outcome arrives on the stream."
  [place]
  (storage/mount! (assoc (peer-request (circle/nearby) place) :credentials smb/credentials)))
