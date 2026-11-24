(ns schema.ujima.shares
  "A folder shared with the circle, as the verbs take it and ujima.circle.self keeps it:
   {:place [:usb \"6962-5E15\"] :path \"Grade 5/math\" :name \"math\" :access :rw}")


(def place-id   [:tuple [:enum :local :usb :session] :string])
(def share-name [:re {:error/message "1-64 characters, none of / \\ : * ? \" < > |, not starting with a dot, not ending in a dot or a space"}
                 #"^(?!\.)(?!.*[. ]\z)[^/\\:*?\"<>|\p{Cntrl}]{1,64}\z"])
(def share-path [:re {:error/message "a relative path inside the place, no .. segments"}
                 #"^(?!.*(^|/)\.\.(/|$))[^/].*$"])
(def share      [:map
                 [:place  place-id]
                 [:path   share-path]
                 [:name   share-name]
                 [:access [:enum :ro :rw]]])

(def offered    "A share as a peer publishes it."
                [:map
                 [:name   share-name]
                 [:access [:enum :ro :rw]]])

(defn name-key
  "Names that differ only in case are one name: \"Math\" -> \"math\"."
  [name]
  (.toLowerCase ^String name java.util.Locale/ROOT))
