(ns ujima.desktop.places-test
  (:require [clojure.test :refer [deftest is testing]]
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

(def ^:private root "/places/ujima")

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


(deftest a-fresh-home-gets-a-link-per-place-except-its-own-dir
  (let [plan (places/plan root (places/wanted places) {"Temporary" {:type :dir}})]
    (is (= {"This Computer" "/ujima/storage/files"
            "USB Stick"     "/ujima/run/storage/6962-5E15/files"}
           (:link plan))
        "Temporary's storage root IS ~/Temporary — the dir is the entry, no link")
    (is (= [] (:unlink plan)))
    (is (= [] (:conflict plan)))))


(deftest a-link-nobody-wants-goes-and-a-moved-one-is-remade
  (let [existing {"This Computer" {:type :symlink :target "/ujima/storage/files"}
                  "USB Stick"     {:type :symlink :target "/ujima/run/storage/OLD-UUID/files"}
                  "USB Stick 2"   {:type :symlink :target "/ujima/run/storage/GONE/files"}
                  "Temporary"     {:type :dir}}
        plan     (places/plan root (places/wanted places) existing)]
    (is (= {"USB Stick" "/ujima/run/storage/6962-5E15/files"} (:link plan)) "This Computer is already right")
    (is (= #{"USB Stick 2" "USB Stick"} (set (:unlink plan))) "stale link gone, moved link unlinked before relinking")))


(deftest a-real-entry-in-the-way-is-a-conflict-never-a-casualty
  (let [existing {"This Computer" {:type :dir}
                  "notes.txt"     {:type :file}}
        plan     (places/plan root (places/wanted places) existing)]
    (is (= [{:type :dir :name "This Computer" :wanted "/ujima/storage/files"}] (:conflict plan)))
    (is (not (contains? (:link plan) "This Computer")))
    (is (= [] (:unlink plan)) "a plain file is not ours to remove")))


(deftest apply-makes-and-unmakes-links-on-a-real-dir
  (let [home   (str (fs/create-temp-dir))
        target (str (fs/create-temp-dir))]
    (try
      (testing "links appear"
        (places/apply! home (places/plan home {"This Computer" target} {}))
        (is (fs/sym-link? (fs/path home "This Computer")))
        (is (= target (str (fs/read-link (fs/path home "This Computer"))))))
      (testing "a real dir with a place's name survives a conflict"
        (fs/create-dirs (fs/path home "USB Stick"))
        (fs/create-file (fs/path home "USB Stick" "keep.txt"))
        (places/apply! home (places/plan home {"This Computer" target "USB Stick" "/nowhere"}
                                     {"This Computer" {:type :symlink :target target}
                                      "USB Stick"     {:type :dir}}))
        (is (fs/exists? (fs/path home "USB Stick" "keep.txt"))))
      (testing "an unwanted link goes, its target untouched"
        (fs/create-file (fs/path target "data.txt"))
        (places/apply! home (places/plan home {} {"This Computer" {:type :symlink :target target}}))
        (is (not (fs/exists? (fs/path home "This Computer") {:nofollow-links true})))
        (is (fs/exists? (fs/path target "data.txt"))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree target)))))


(deftest no-root-means-off
  (places/init! {})
  (is (nil? (places/converge! [] nil)) "a dev host without :places-home never touches a home"))
