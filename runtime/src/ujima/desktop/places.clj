(ns ujima.desktop.places
  "Places, presented. Two projections of the storage plane's entries, from ONE source of names:
   the wire blob that /stream/places carries (the Files app's cards), and the user's home — the
   Places screen IS home: one entry per ready place, named as its card, so a dialog opened at ~
   shows the same places the Files app does and GTK's crumbs collapse behind the home button. A
   place whose storage root already is ~/<name> (Temporary) is its own entry; every other place
   is a symlink. Full reconcile on each storage converge: missing links made, stale links removed,
   and nothing that is not a symlink is ever touched — a real file or dir where a link belongs is
   a conflict, logged and left alone."
  (:require [clojure.string :as str]
            [babashka.fs    :as fs]
            [ujima.log      :as log]))


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

(defonce ^:private root* (atom nil))

(defn init!
  "PLACES-HOME is the dir that plays the Places screen; nil = off."
  [{:keys [places-home]}]
  (reset! root* places-home))


(defn wanted
  "The places blob -> {name storage-root} for the places that are ready to browse."
  [places]
  (into {} (for [{:keys [state name storage]} places
                 :when (and (= :ready state) storage)]
             [name (str (fs/path storage))])))


(defn- scan
  "ROOT's visible entries -> {name {:type :symlink|:dir|:file :target}}. Dot entries are the
   apps' own business and never looked at."
  [root]
  (into {} (for [p     (fs/list-dir root)
                 :let  [name (fs/file-name p)]
                 :when (not (str/starts-with? name "."))]
             [name (cond (fs/sym-link? p)  {:type :symlink :target (str (fs/read-link p))}
                         (fs/directory? p) {:type :dir}
                         :else             {:type :file})])))


(defn plan
  "WANTED {name target} against EXISTING (scan) -> {:link {name target} :unlink [names]
   :conflict [entries]}. Pure. A link whose target moved is unlinked then linked."
  [root wanted existing]
  (let [own?  (fn [name target] (= (str (fs/path root name)) target))
        stale (vec (for [[name {:keys [type]}] existing
                         :when (and (= :symlink type) (not (contains? wanted name)))]
                     name))]
    (reduce-kv
     (fn [plan name target]
       (let [{:keys [type] :as entry} (get existing name)]
         (cond
           (nil? entry)         (update plan :link assoc name target)
           (= :symlink type)    (if (= target (:target entry))
                                  plan
                                  (-> plan (update :unlink conj name) (update :link assoc name target)))
           (own? name target)   plan
           :else                (update plan :conflict conj (assoc entry :name name :wanted target)))))
     {:link {} :unlink stale :conflict []}
     wanted)))


(defn apply!
  "Effect a plan on ROOT. Unlinks first, so a moved link is remade cleanly."
  [root {:keys [link unlink conflict]}]
  (doseq [name unlink]
    (fs/delete (fs/path root name)))                 ; the link itself — never its target
  (doseq [[name target] link]
    (fs/create-sym-link (fs/path root name) target))
  (doseq [entry conflict]
    (log/error "places: something that is not a link sits where a place belongs — left alone" entry))
  (when (or (seq link) (seq unlink))
    (log/info "places: home reconciled" {:linked (vec (keys link)) :unlinked unlink})))


(defn converge!
  "The storage arrow: entries -> home. Fails loud in the log, never into the plane."
  [entries _prv]
  (when-some [root @root*]
    (try
      (fs/create-dirs root)
      (let [places (:places (places->ui entries))]
        (apply! root (plan root (wanted places) (scan root))))
      (catch Throwable e
        (log/error "places: home projection failed" {:root root :error (ex-message e)})))))
