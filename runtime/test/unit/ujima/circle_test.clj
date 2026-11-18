(ns ujima.circle-test
  "One datagram over the real socket, into the peers."
  (:require [clojure.test :refer [deftest is]]
            [ujima.circle      :as circle]
            [ujima.circle.beat :as beat])
  (:import  [java.net DatagramPacket DatagramSocket InetAddress]))


(def ^:private circle-key "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef")


(deftest a-datagram-on-the-wire-lands-among-the-peers
  (reset! @#'circle/peers* {})
  (let [sock  (DatagramSocket. 0)
        port  (.getLocalPort sock)
        _     (#'circle/listen! sock {:key circle-key :id "me"})
        send! (fn [text]
                (let [b (.getBytes ^String text "UTF-8")]
                  (.send (DatagramSocket.) (DatagramPacket. b (count b) (InetAddress/getByName "127.0.0.1") port))))]
    (send! (beat/encode circle-key {:id "me"       :host "127.0.0.1" :port 1    :boot "b" :seq 0 :hash "h"}))
    (send! (beat/encode "cafe"     {:id "stranger" :host "127.0.0.1" :port 1    :boot "b" :seq 0 :hash "h"}))
    (send! (beat/encode circle-key {:id "liar"     :host "10.0.0.9"  :port 1    :boot "b" :seq 0 :hash "h"}))
    (send! (beat/encode circle-key {:id "them"     :host "127.0.0.1" :port 1337 :boot "b" :seq 0 :hash "h"}))
    (let [deadline (+ (System/currentTimeMillis) 3000)]
      (while (and (empty? (circle/snapshot)) (< (System/currentTimeMillis) deadline))
        (Thread/sleep 20)))
    (is (= [{:id "them" :address {:host "127.0.0.1" :port 1337} :state :online}] (circle/snapshot))
        "ours lands; our own beat, another circle's, and one naming an address it did not come from do not")
    (circle/forget! "them")
    (is (= [] (circle/snapshot)))
    (.close sock)))
