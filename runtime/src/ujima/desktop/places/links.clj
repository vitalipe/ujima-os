(ns ujima.desktop.places.links
  "A directory of symlinks, reconciled to a wanted {name target}: missing links made, stale
   links removed, and nothing that is not a symlink is ever touched — a real file or dir where
   a link belongs is a conflict, logged and left alone."
  (:require [clojure.string :as str]
            [babashka.fs    :as fs]
            [ujima.log      :as log]))


(defn scan
  "ROOT's visible entries -> {name {:type :symlink|:dir|:file :target}}. Dot entries are
   never looked at."
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
    (log/error "links: something that is not a link sits where one belongs — left alone" entry))
  (when (or (seq link) (seq unlink))
    (log/info "links: reconciled" {:root (str root) :linked (vec (keys link)) :unlinked unlink})))


(defn reconcile!
  "ROOT, created if missing, holds exactly the links WANTED."
  [root wanted]
  (fs/create-dirs root)
  (apply! root (plan root wanted (scan root))))
