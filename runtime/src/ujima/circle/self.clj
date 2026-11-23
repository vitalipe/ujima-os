(ns ujima.circle.self
  "This machine as the circle sees it: its name, the folders it shares, the hash its beat
   carries. A share is session state with verbs, not a setting: share! and unshare! write the
   list, the settings arrow brings the name, the storage arrow the places; every write
   projects, and targets hear (next prev):
     {:name      \"Mvule\"
      :shares    [{:place [:usb \"6962-5E15\"] :path \"Grade 5/math\" :name \"math\" :access :rw}]
      :published [{:name \"math\" :access :rw}]        served now — the share's place is mounted
      :hash      \"0112381b\"}"
  (:require [clojure.string      :as str]
            [malli.core          :as m]
            [malli.error         :as me]
            [lib.util            :refer [find-first]]
            [schema.ujima.shares :as schema]
            [ujima.circle.beat   :as beat]))


(def ^:private empty-state {:name nil :shares [] :entries []})

(defonce ^:private state*   (atom empty-state))   ; what the arrows and the verbs wrote
(defonce ^:private prev*    (atom nil))           ; last projection, the prev of (next prev)
(defonce ^:private targets* (atom []))
(defonce ^:private lock     (Object.))


;; --- the projection ----------------------------------------------------------

(defn- published
  "SHARES, ENTRIES -> what is served now, by name; a share whose place is not mounted is not."
  [shares entries]
  (let [mounted (into #{} (for [{:keys [kind name state]} entries :when (= :mounted state)] [kind name]))]
    (->> shares
         (filter (comp mounted :place))
         (map #(select-keys % [:name :access]))
         (sort-by :name)
         (into []))))

(defn- projection [{:keys [name shares entries]}]
  (let [published (published shares entries)]
    {:name      name
     :shares    shares
     :published published
     :hash      (beat/digest {:name name :shares published})}))

(defn- project! []
  (let [next (projection @state*)
        prev @prev*]
    (reset! prev* next)
    (doseq [t @targets*] (t next prev))))


;; --- the verbs ---------------------------------------------------------------

(defn- canonical
  "\"./Grade 5//math/\" -> \"Grade 5/math\". A leading slash and `..` stay, for the shape to refuse."
  [path]
  (str (when (str/starts-with? (str path) "/") "/")
       (str/join "/" (remove #{"" "."} (str/split (str path) #"/")))))

(defn- same-folder? [place path share]
  (and (= place (:place share)) (= path (:path share))))

(defn- valid! [share]
  (when-not (m/validate schema/share share)
    (throw (ex-info (->> (me/humanize (m/explain schema/share share)) (tree-seq coll? seq) (filter string?) first)
                    {:error :request/malformed :share share}))))


(defn share!
  "{:place [:usb \"6962-5E15\"] :path \"./Grade 5//math/\"} ->
   {:place [:usb \"6962-5E15\"] :path \"Grade 5/math\" :name \"math\" :access :ro}, as kept.
   The same folder again changes its :name or :access."
  [{:keys [place path access] wanted-name :name}]
  (locking lock
    (let [path   (canonical path)
          own    (:shares @state*)
          before (find-first (partial same-folder? place path) own)
          others (into [] (remove (partial same-folder? place path)) own)
          share  {:place  place
                 :path   path
                 :name   (or wanted-name (:name before) (last (str/split path #"/")))
                 :access (or access (:access before) :ro)}]
      (valid! share)
      (when (some #(= (:name share) (:name %)) others)
        (throw (ex-info "a share by that name exists" {:error :shares/name-taken :name (:name share)})))
      (swap! state* assoc :shares (conj others share))
      (project!)
      share)))

(defn unshare!
  "Unshare the folder at PATH inside PLACE."
  [{:keys [place path]}]
  (locking lock
    (let [path (canonical path)
          own  (:shares @state*)]
      (when-not (some (partial same-folder? place path) own)
        (throw (ex-info "no such share" {:error :shares/unknown-share :place place :path path})))
      (swap! state* assoc :shares (into [] (remove (partial same-folder? place path)) own))
      (project!)
      {:removed [place path]})))


;; --- the arrows in, the arrow out ---------------------------------------------

(defn on-settings!
  "The settings arrow: the name."
  [settings _prv]
  (locking lock
    (swap! state* assoc :name (:effective (get settings [:system :name])))
    (project!)))

(defn on-storage!
  "The storage arrow: a share's place came or went."
  [entries _prv]
  (locking lock
    (swap! state* assoc :entries entries)
    (project!)))

(defn on-project!
  "Attach a target: F gets (next, prev) after every write, under the lock."
  [f]
  (swap! targets* conj f)
  nil)

(defn snapshot [] (or @prev* (projection empty-state)))

(defn init! []
  (reset! state*   empty-state)
  (reset! prev*    nil)
  (reset! targets* [])
  nil)
