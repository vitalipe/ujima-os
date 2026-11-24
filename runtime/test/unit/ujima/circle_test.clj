(ns ujima.circle-test
  "The plane: a datagram over the real socket, and the read of a peer as a pass."
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.async  :as async]
            [lib.task.probe      :as probe]
            [ujima.circle        :as circle]
            [ujima.circle.beat   :as beat]
            [ujima.circle.client :as client])
  (:import  [java.net DatagramPacket DatagramSocket InetAddress]))


(def ^:private circle-key "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef")


(defn- fresh!
  "Under the lock, so a read still landing from the last test finishes first."
  []
  (locking @#'circle/lock
    (#'circle/reset-state! {:key circle-key :id "me"})
    (reset! @#'circle/targets* [])))

(defn- settle
  "PRED's value once it holds, or after 3 s."
  [pred]
  (let [deadline (+ (System/currentTimeMillis) 3000)]
    (while (and (not (pred)) (< (System/currentTimeMillis) deadline))
      (Thread/sleep 20))
    (pred)))

(defn- a-tree
  "A peer's tier as a read sees it: NAME and SHARES answer the two nodes asked for."
  [name shares]
  (fn [_peer path]
    (case path
      "system/name" {:status :ok :data {:name name}}
      "shares"      (cond-> {:status :ok} (seq shares) (assoc :data shares)))))

(defn- a-beat [id host port seq hash & [boot]]
  (beat/encode circle-key {:id id :host host :port port :boot (or boot "b") :seq seq :hash hash}))

(defn- read-of [id] (get-in @@#'circle/reads* [id :probe]))

(defn- failed-at [id] (#'circle/failed-at (probe/probe->task (read-of id))))


(deftest a-datagram-on-the-wire-lands-among-the-peers
  (fresh!)
  (with-redefs [client/node (a-tree "Them" [])]
    (let [sock  (DatagramSocket. 0)
          port  (.getLocalPort sock)
          _     (#'circle/listen! sock)
          send! (fn [text]
                  (let [b (.getBytes ^String text "UTF-8")]
                    (.send (DatagramSocket.) (DatagramPacket. b (count b) (InetAddress/getByName "127.0.0.1") port))))]
      (send! (a-beat "me" "127.0.0.1" 1 0 "h"))
      (send! (beat/encode "cafe" {:id "stranger" :host "127.0.0.1" :port 1 :boot "b" :seq 0 :hash "h"}))
      (send! (a-beat "liar" "10.0.0.9" 1 0 "h"))
      (send! (a-beat "them" "127.0.0.1" 1337 0 "h"))
      (is (settle #(= "Them" (:name (first (circle/snapshot))))) "heard, then read")
      (is (= [{:id "them" :address {:host "127.0.0.1" :port 1337} :state :online :name "Them" :shares []}]
             (circle/snapshot))
          "ours lands; our own beat, another circle's, and one naming an address it did not come from do not")
      (circle/forget! "them")
      (is (= [] (circle/snapshot)))
      (.close sock))))


(deftest a-peer-is-read-when-first-heard-and-again-when-its-hash-moves
  (fresh!)
  (let [tree  (atom (a-tree "Mvule" [{:name "math" :access :rw}]))
        reads (atom 0)
        heard (atom [])]
    (circle/on-project! (fn [next prev] (swap! heard conj [(mapv :name next) (mapv :name prev)])))
    (with-redefs [client/node (fn [peer path] (swap! reads inc) (@tree peer path))]
      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 0 "h1"))
      (is (settle #(= "Mvule" (:name (first (circle/snapshot))))))
      (is (= [{:id "x" :address {:host "10.0.0.5" :port 1337} :state :online
               :name "Mvule" :shares [{:name "math" :access :rw}]}]
             (circle/snapshot)))
      (is (= [{:peer-id "x" :address {:host "10.0.0.5" :port 1337} :host "Mvule" :label "math" :access :rw}]
             (circle/nearby)))
      (is (= 2 @reads) "one read: the name and the shares")
      (is (= [[[nil] []] [["Mvule"] [nil]]] @heard) "heard once when x appeared, once when it was read")

      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 1 "h1"))
      (#'circle/tick!)
      (is (= 2 @reads) "the same hash again is not read again")
      (is (= 2 (count @heard)) "and a beat with nothing new, or the tick, says nothing")

      (reset! tree (a-tree "Mvule" []))
      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 2 "h2"))
      (is (settle #(= [] (:shares (first (circle/snapshot))))) "a moved hash is read again")
      (is (= 4 @reads))
      (is (= [] (circle/nearby)))
      (is (= 3 (count @heard)) "the shares it brought are what moved")

      (reset! tree (a-tree "Mvule" [{:name "art" :access :ro}]))
      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 0 "h3" "b2"))
      (is (settle #(= ["art"] (mapv :name (:shares (first (circle/snapshot)))))) "a new boot is read again")
      (is (= 6 @reads))
      (is (= [[[nil] ["Mvule"]] [["Mvule"] [nil]]] (subvec @heard 3))
          "and knows nothing of x until it lands: the session that was read is over"))))


(deftest a-share-that-is-not-a-plain-name-is-dropped-on-read
  (fresh!)
  (let [bad [{:name "../../../../../etc" :access :ro} {:name "/etc" :access :rw} {:name ".." :access :ro}
             {:name "a/b" :access :ro} {:name "" :access :ro} {:name "math\n" :access :ro}
             {:name (apply str (repeat 65 "a")) :access :ro} {:name "art" :access "rw"} {:access :ro} "junk"]]
    (with-redefs [client/node (a-tree "Mvule" (into [{:name "math" :access :rw}] bad))]
      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 0 "h1"))
      (is (settle #(= "Mvule" (:name (first (circle/snapshot))))))
      (is (= [{:name "math" :access :rw}] (:shares (first (circle/snapshot)))))
      (is (= ["math"] (mapv :label (circle/nearby)))))
    (with-redefs [client/node (fn [_ path] {:status :ok :data (case path "system/name" {:name "Mvule"} "shares" {:name "math"})})]
      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 1 "h2"))
      (is (settle #(= [] (:shares (first (circle/snapshot))))) "an answer that is not a list offers nothing"))))


(deftest a-read-that-failed-is-tried-again-later-not-every-beat
  (fresh!)
  (let [reads (atom 0)]
    (with-redefs [client/node (fn [_ _] (swap! reads inc) {:status :noreply :data {:reason :transport}})]
      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 0 "h1"))
      (is (settle #(= :error (probe/probe->state (read-of "x")))) "the read landed as failed")
      (is (= [nil []] ((juxt :name :shares) (first (circle/snapshot)))) "nothing known of x yet")
      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 1 "h1"))
      (#'circle/tick!)
      (is (= 2 @reads) "not on the next beat, nor the tick")
      (let [first-failure (failed-at "x")]
        (with-redefs [circle/retry-ms 50]
          (Thread/sleep 60)
          (#'circle/tick!)
          (is (settle #(and (= 4 @reads)
                            (= :error (probe/probe->state (read-of "x")))
                            (< first-failure (failed-at "x"))))
              "after retry-ms it is — once, and it failed again"))))))


(deftest the-present-peers-are-watched-as-events-only-when-the-set-moves
  (fresh!)
  (let [ch    (circle/watch-peers!)
        taken (fn [] (async/alt!! ch ([v] v) (async/timeout 500) :nothing))]
    (with-redefs [client/node (a-tree "Mvule" [])]
      (#'circle/heard! "10.0.0.5" (a-beat "x" "10.0.0.5" 1337 0 "h1"))
      (is (= {:type :circle/peers :peers #{"x"}} (taken)) "heard: present")
      (is (settle #(= "Mvule" (:name (first (circle/snapshot))))))
      (is (= :nothing (taken)) "the read that landed moved the projection, not who is present")
      (circle/forget! "x")
      (is (= {:type :circle/peers :peers #{}} (taken))))))
