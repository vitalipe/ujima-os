(ns ujima.desktop.places.links-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [ujima.desktop.places.links :as links]))


(def ^:private root "/places/ujima")

(def ^:private wanted
  {"This Computer" "/ujima/storage/files"
   "Temporary"     "/places/ujima/Temporary"
   "USB Stick"     "/ujima/run/storage/6962-5E15/files"})


(deftest a-fresh-root-gets-a-link-per-entry-except-its-own-dir
  (let [plan (links/plan root wanted {"Temporary" {:type :dir}})]
    (is (= {"This Computer" "/ujima/storage/files"
            "USB Stick"     "/ujima/run/storage/6962-5E15/files"}
           (:link plan))
        "Temporary's target IS root/Temporary — the dir is the entry, no link")
    (is (= [] (:unlink plan)))
    (is (= [] (:conflict plan)))))


(deftest a-link-nobody-wants-goes-and-a-moved-one-is-remade
  (let [existing {"This Computer" {:type :symlink :target "/ujima/storage/files"}
                  "USB Stick"     {:type :symlink :target "/ujima/run/storage/OLD-UUID/files"}
                  "USB Stick 2"   {:type :symlink :target "/ujima/run/storage/GONE/files"}
                  "Temporary"     {:type :dir}}
        plan     (links/plan root wanted existing)]
    (is (= {"USB Stick" "/ujima/run/storage/6962-5E15/files"} (:link plan)) "This Computer is already right")
    (is (= #{"USB Stick 2" "USB Stick"} (set (:unlink plan))) "stale link gone, moved link unlinked before relinking")))


(deftest a-real-entry-in-the-way-is-a-conflict-never-a-casualty
  (let [existing {"This Computer" {:type :dir}
                  "notes.txt"     {:type :file}}
        plan     (links/plan root wanted existing)]
    (is (= [{:type :dir :name "This Computer" :wanted "/ujima/storage/files"}] (:conflict plan)))
    (is (not (contains? (:link plan) "This Computer")))
    (is (= [] (:unlink plan)) "a plain file is not ours to remove")))


(deftest reconcile-makes-and-unmakes-links-on-a-real-dir
  (let [dir    (str (fs/create-temp-dir))
        home   (str dir "/home")
        target (str (fs/create-temp-dir))]
    (try
      (testing "the root is made, links appear"
        (links/reconcile! home {"This Computer" target})
        (is (fs/sym-link? (fs/path home "This Computer")))
        (is (= target (str (fs/read-link (fs/path home "This Computer"))))))
      (testing "a real dir with a link's name survives a conflict"
        (fs/create-dirs (fs/path home "USB Stick"))
        (fs/create-file (fs/path home "USB Stick" "keep.txt"))
        (links/reconcile! home {"This Computer" target "USB Stick" "/nowhere"})
        (is (fs/exists? (fs/path home "USB Stick" "keep.txt"))))
      (testing "an unwanted link goes, its target untouched"
        (fs/create-file (fs/path target "data.txt"))
        (links/reconcile! home {})
        (is (not (fs/exists? (fs/path home "This Computer") {:nofollow-links true})))
        (is (fs/exists? (fs/path target "data.txt"))))
      (finally
        (fs/delete-tree dir)
        (fs/delete-tree target)))))
