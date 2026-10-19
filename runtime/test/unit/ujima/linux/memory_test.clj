(ns ujima.linux.memory-test
  (:require [clojure.test :refer [deftest is]]
            [babashka.fs        :as fs]
            [ujima.linux.memory :as memory]))


(def ^:private psi
  "some avg10=1.50 avg60=0.40 avg300=0.10 total=4535\nfull avg10=17.25 avg60=3.00 avg300=0.70 total=4138\n")


(deftest full-avg10-reads-the-full-line
  (is (= 17.25 (memory/full-avg10 psi)))
  (is (nil? (memory/full-avg10 "")) "no PSI, no reading"))



(defn- fake-scope! [root name anon swap]
  (let [d (fs/path root name)]
    (fs/create-dirs d)
    (spit (str (fs/path d "memory.stat")) (str "anon " anon "\nfile 99\n"))
    (spit (str (fs/path d "memory.swap.current")) (str swap "\n"))
    (str d)))


(deftest app-scopes-report-what-a-kill-gives-back
  (let [root  (str (fs/create-temp-dir))
        godot (fake-scope! root "ujima-app-godot-11.scope" 600 50)]
    (fake-scope! root "ujima-shell.service" 70 0)
    (spit (str (fs/path root "memory.stat")) "anon 1\n")
    (is (= [{:app-id :godot :dir godot :bytes 650}] (memory/app-scopes root))
        "anon + swap, our scopes only")
    (is (nil? (memory/app-scopes (str (fs/path root "nope")))) "no slice, no scopes")))


(deftest kill-scope-writes-the-cgroup-kill-knob
  (let [d (str (fs/create-temp-dir))]
    (memory/kill-scope! d)
    (is (= "1" (slurp (str (fs/path d "cgroup.kill")))))))

