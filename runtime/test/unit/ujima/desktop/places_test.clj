(ns ujima.desktop.places-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [ujima.desktop.places :as places]
            [lib.edn :refer [edn->json]]))


;; --- places -----------------------------------------------------------------

(def ^:private mounted-entry
  {:uuid "6962-5E15" :disk "sda" :kind :usb :name "6962-5E15"
   :label nil :fstype "vfat" :rm true
   :state :mounted :mount "/ujima/run/storage/6962-5E15"
   :storage "/ujima/run/storage/6962-5E15"
   :tokens {:circle/secret {:key "abc"}}})

(def ^:private ujstore-entry
  {:label "UJSTORE" :fstype "ext4" :kind :local :name "storage"
   :state :mounted :mount "/ujima/storage"
   :storage "/ujima/storage/files" :tokens {}})

(defn- usb-place [entry]
  (first (:places (places/places->ui [entry]))))


(deftest a-machine-partition-projects-as-the-local-place
  (is (= {:id [:local "storage"] :kind :local :name "This Computer" :state :ready
          :label "UJSTORE" :fstype "ext4"
          :mount "/ujima/storage" :storage "/ujima/storage/files" :tokens []}
         (usb-place ujstore-entry))
      "the label's convention names the place; :apps stays off this wire"))


(deftest a-mounted-partition-is-a-ready-usb-place
  (is (= {:id      [:usb "6962-5E15"] :kind :usb :name "USB Stick" :state :ready
          :mount   "/ujima/run/storage/6962-5E15"
          :storage "/ujima/run/storage/6962-5E15"
          :label   nil :fstype "vfat"
          :tokens  ["circle/secret"]}
         (usb-place mounted-entry))
      "plane plumbing (:rm :disk) stays behind; the root rides along so a registration
       resolves; tokens flatten to type names, ns/name strings since the wire drops namespaces"))


(deftest token-values-never-reach-the-wire
  (let [rendered (edn->json (places/places->ui [mounted-entry]))]
    (is (str/includes? rendered "circle/secret") "the type is the finding")
    (is (not (str/includes? rendered "abc"))
        "no value survives serialization — asserted over the WHOLE rendered output,
         so a later entry carrying a value cannot slip through a per-field check")))


(deftest an-unmounted-machine-partition-projects-invalid
  (is (= {:id [:local "storage"] :kind :local :name "This Computer" :state :invalid
          :label "UJSTORE" :fstype "ext4" :reason "not mounted: /ujima/storage"}
         (usb-place {:label "UJSTORE" :fstype "ext4" :kind :local :name "storage"
                     :state :invalid :reason "not mounted: /ujima/storage"}))))


(deftest an-invalid-partition-carries-why-and-no-storage-root
  (is (= {:id [:usb "X"] :kind :usb :name "USB Stick" :state :invalid :label "KEYS" :fstype "vfat"
          :reason "mount: fail"}
         (usb-place {:kind :usb :name "X" :state :invalid :label "KEYS"
                     :fstype "vfat" :reason "mount: fail"}))))


(deftest in-flight-states-read-as-mounting
  (is (= :mounting (:state (usb-place {:kind :usb :name "X" :state :detected})))
      "detected is in flight by the time anyone sees it")
  (is (= :mounting (:state (usb-place {:kind :usb :name "X" :state :mounting})))))


;; --- names: the card title = the folder in every dialog ----------------------

(def ^:private session-entry
  {:kind :session :name "files" :state :mounted :mount "/home/ujima/Temporary"
   :storage "/home/ujima/Temporary" :tokens {}})

(defn- names [entries]
  (mapv :name (:places (places/places->ui entries))))

(deftest a-place-carries-its-display-name
  (is (= ["This Computer" "Temporary" "USB Stick"]
         (names [ujstore-entry session-entry mounted-entry]))))

(deftest one-stick-keeps-the-bare-name
  (is (= ["USB Stick"] (names [mounted-entry]))))

(deftest two-sticks-are-numbered-in-label-order
  (let [labelled (assoc mounted-entry :uuid "1111-AAAA" :name "1111-AAAA" :label "ZED")
        other    (assoc mounted-entry :uuid "2222-BBBB" :name "2222-BBBB" :label "ALPHA")]
    (is (= ["USB Stick 2" "USB Stick"] (names [labelled other]))
        "ALPHA sorts first and stays bare; ZED becomes the second stick")
    (is (= ["USB Stick 2" "USB Stick" "This Computer"] (names [labelled other ujstore-entry]))
        "only sticks take numbers")))

(deftest a-stick-still-mounting-is-numbered-with-the-rest
  (let [mounting (assoc mounted-entry :uuid "2222-BBBB" :name "2222-BBBB" :label "B" :state :detected)]
    (is (= ["USB Stick" "USB Stick 2"] (names [(assoc mounted-entry :label "A") mounting]))
        "numbering is by presence, not readiness — a stick keeps its number when it becomes ready")))


;; --- home: the Places screen on disk -----------------------------------------

(def ^:private places
  [{:id [:local "storage"] :kind :local :name "This Computer" :state :ready :storage "/ujima/storage/files"}
   {:id [:session "files"] :kind :session :name "Temporary" :state :ready :storage "/places/ujima/Temporary"}
   {:id [:usb "6962-5E15"] :kind :usb :name "USB Stick" :state :ready :storage "/ujima/run/storage/6962-5E15/files/"}
   {:id [:usb "DEAD-BEEF"] :kind :usb :name "USB Stick 2" :state :mounting}])


(deftest wanted-is-the-ready-places-by-name
  (is (= {"This Computer" "/ujima/storage/files"
          "Temporary"     "/places/ujima/Temporary"
          "USB Stick"     "/ujima/run/storage/6962-5E15/files"}
         (places/wanted places))
      "a place still mounting has no root yet; a trailing slash is normalized away"))


(deftest no-root-means-off
  (places/init! {})
  (is (nil? (places/converge! [] nil)) "a dev host without :places-home never touches a home"))


;; --- the by-id root: every mounted place, by id -------------------------------

(deftest a-mounted-place-is-reachable-by-id
  (let [dir (str (fs/create-temp-dir))]
    (fs/create-dirs (fs/path dir "usb"))
    (fs/create-sym-link (fs/path dir "usb" "OLD-UUID") "/ujima/run/storage/OLD-UUID")
    (places/init! {:places-dir dir})
    (places/converge! [ujstore-entry (assoc mounted-entry :state :detected)] nil)
    (is (= (str dir "/local/storage") (places/place-path [:local "storage"])))
    (is (= "/ujima/storage/files" (str (fs/read-link (fs/path dir "local" "storage"))))
        "the link lands on the storage root, not the mount")
    (is (not (fs/exists? (fs/path dir "usb" "OLD-UUID") {:nofollow-links true}))
        "a previous run's stick is gone from every kind dir")
    (is (not (fs/exists? (fs/path dir "usb" "6962-5E15") {:nofollow-links true}))
        "a stick still mounting has no link yet")
    (places/init! {})
    (is (= "/ujima/run/places/usb/6962-5E15" (places/place-path [:usb "6962-5E15"]))
        "unconfigured, the path is the machine's")))

