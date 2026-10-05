(ns ujima.desktop.http.files
  "What the shell answers with a file: the wall from its own tree, containment-checked,
   and the app icons, which are catalog-resolved and live elsewhere."
  (:require [clojure.string  :as str]
            [clojure.java.io :as io]
            [ujima.desktop.app :as app]))


(def ^:private content-types
  {"png" "image/png"})


;; --- file serving: statics + the app icon --------------------------------

(defn- serve
  "A file from the shell tree at STATIC-ROOT, or nil — containment checked after resolution."
  [static-root f]
  (let [root (str (.getCanonicalPath (io/file static-root)) "/")
        ext  (some-> (re-find #"\.([^.]+)$" (.getName f)) second str/lower-case)]
    (when (and (.isFile f) (str/starts-with? (.getCanonicalPath f) root))
      {:status 200
       :headers {"content-type" (get content-types ext "application/octet-stream")}
       :body f})))

(defn icon-file
  "The catalog-resolved icon, so the launcher never sees the layout."
  [id]
  (let [f (some-> (app/icon-path (keyword id)) io/file)]
    (if (and f (.isFile f))
      {:status 200 :headers {"content-type" "image/svg+xml"} :body f}
      {:status 404 :body {:error "unknown app"}})))


(defn wall [static-root name] (serve static-root (io/file static-root name)))
