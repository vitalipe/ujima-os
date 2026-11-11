(ns ujima.circle-test
  "The heartbeat and the roster it feeds: what verifies, what a replay does, how
   presence ages, and one datagram over the real socket."
  (:require [clojure.test :refer [deftest is]]
            [ujima.circle :as circle])
  (:import  [java.net DatagramPacket DatagramSocket InetAddress]))


(def ^:private circle-key "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef")

(def ^:private admit  #'circle/admit)
(def ^:private render #'circle/render)

(def ^:private beat {:id "node-a" :host "10.0.0.5" :port 1337 :boot "b7c1e2f4" :seq 3})


(deftest a-beat-round-trips-and-nothing-else-is-one
  (is (= beat (circle/decode circle-key (circle/encode circle-key beat))))
  (is (= "039bac1fb04744b0fc3c7c2a5e8aa9a7c0f834dcc07896cc0169176a9d0a86ed"
         (:sig (read-string (circle/encode circle-key beat))))
      "the wire format, frozen")
  (is (nil? (circle/decode "cafe" (circle/encode circle-key beat))) "another circle's key")
  (is (nil? (circle/decode circle-key (pr-str (assoc beat :sig "00")))) "a wrong signature")
  (is (nil? (circle/decode circle-key (pr-str beat))) "no signature")
  (is (nil? (circle/decode circle-key "{:id \"x\"")) "noise")
  (is (nil? (circle/decode circle-key (circle/encode circle-key (assoc beat :id 7)))) "an id that is not a string"))


(deftest the-roster-keeps-the-latest-beat-and-ignores-one-already-heard
  (let [r (admit {} beat 1000)]
    (is (= {:host "10.0.0.5" :port 1337 :boot "b7c1e2f4" :seq 3 :seen-ms 1000} (r "node-a")))
    (is (identical? r (admit r beat 2000)) "the same beat again is a replay")
    (is (identical? r (admit r (assoc beat :seq 2) 2000)) "an older one arrived late")
    (is (= 3000 (:seen-ms ((admit r (assoc beat :seq 4) 3000) "node-a"))) "the next one counts")
    (is (= ["b9" 0 "10.0.0.7"]
           ((juxt :boot :seq :host) ((admit r (assoc beat :boot "b9" :seq 0 :host "10.0.0.7") 4000) "node-a")))
        "a new boot starts the count over, wherever it now is")))


(deftest presence-is-how-long-since-the-last-beat
  (let [r {"b" {:host "h" :port 1 :seen-ms 0} "a" {:host "h" :port 1 :seen-ms 0}}]
    (is (= ["a" "b"] (map :id (render r 0))) "by id")
    (is (= {:id "a" :address {:host "h" :port 1} :state :online} (first (render r 0))))
    (is (= [:online :online :unsure :unsure :offline]
           (for [now [0 3000 3001 7000 7001]] (:state (first (render r now)))))
        "one missed beat is unsure, three is offline")))


(deftest a-datagram-on-the-wire-lands-in-the-roster
  (reset! @#'circle/roster* {})
  (let [sock  (DatagramSocket. 0)
        port  (.getLocalPort sock)
        _     (#'circle/listen! sock {:key circle-key :id "me"})
        send! (fn [text]
                (let [b (.getBytes ^String text "UTF-8")]
                  (.send (DatagramSocket.) (DatagramPacket. b (count b) (InetAddress/getByName "127.0.0.1") port))))]
    (send! (circle/encode circle-key {:id "me"       :host "127.0.0.1" :port 1    :boot "b" :seq 0}))
    (send! (circle/encode "cafe"     {:id "stranger" :host "127.0.0.1" :port 1    :boot "b" :seq 0}))
    (send! (circle/encode circle-key {:id "liar"     :host "10.0.0.9"  :port 1    :boot "b" :seq 0}))
    (send! (circle/encode circle-key {:id "them"     :host "127.0.0.1" :port 1337 :boot "b" :seq 0}))
    (let [deadline (+ (System/currentTimeMillis) 3000)]
      (while (and (empty? (circle/snapshot)) (< (System/currentTimeMillis) deadline))
        (Thread/sleep 20)))
    (is (= [{:id "them" :address {:host "127.0.0.1" :port 1337} :state :online}] (circle/snapshot))
        "ours lands; our own beat, another circle's, and one naming an address it did not come from do not")
    (circle/forget! "them")
    (is (= [] (circle/snapshot)))
    (.close sock)))
