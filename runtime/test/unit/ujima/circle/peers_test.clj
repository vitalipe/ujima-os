(ns ujima.circle.peers-test
  "The peers as data: what a replay does, how presence ages."
  (:require [clojure.test :refer [deftest is]]
            [ujima.circle.peers :as peers]))


(def ^:private a-beat {:id "node-a" :host "10.0.0.5" :port 1337 :boot "b7c1e2f4" :seq 3 :hash "0112381b"})


(deftest the-peers-keep-the-latest-beat-and-ignore-one-already-heard
  (let [r (peers/admit {} a-beat 1000)]
    (is (= {:host "10.0.0.5" :port 1337 :boot "b7c1e2f4" :seq 3 :hash "0112381b" :seen-ms 1000} (r "node-a")))
    (is (identical? r (peers/admit r a-beat 2000)) "the same beat again is a replay")
    (is (identical? r (peers/admit r (assoc a-beat :seq 2) 2000)) "an older one arrived late")
    (is (= 3000 (:seen-ms ((peers/admit r (assoc a-beat :seq 4) 3000) "node-a"))) "the next one counts")
    (is (= ["b9" 0 "10.0.0.7"]
           ((juxt :boot :seq :host) ((peers/admit r (assoc a-beat :boot "b9" :seq 0 :host "10.0.0.7") 4000) "node-a")))
        "a new boot starts the count over, wherever it now is")))


(deftest presence-is-how-long-since-the-last-beat
  (let [r {"b" {:host "h" :port 1 :seen-ms 0} "a" {:host "h" :port 1 :seen-ms 0}}]
    (is (= ["a" "b"] (map :id (peers/render r 0))) "by id")
    (is (= {:id "a" :address {:host "h" :port 1} :state :online} (first (peers/render r 0))))
    (is (= [:online :online :unsure :unsure :offline]
           (for [now [0 3000 3001 7000 7001]] (:state (first (peers/render r now)))))
        "one missed beat is unsure, three is offline")))
