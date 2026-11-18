(ns ujima.circle.beat-test
  "The wire format: what verifies, and the hash it carries."
  (:require [clojure.test :refer [deftest is]]
            [ujima.circle.beat :as beat]))


(def ^:private circle-key "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef")

(def ^:private a-beat {:id "node-a" :host "10.0.0.5" :port 1337 :boot "b7c1e2f4" :seq 3 :hash "0112381b"})


(deftest the-hash-is-eight-hex-over-what-is-published
  (is (= "0112381b" (beat/digest {:name "Mvule" :shares [{:name "math" :access :rw}]}))
      "frozen: a peer compares its own against the beat's, so every machine must agree")
  (is (not= (beat/digest {:name "Mvule" :shares []})
            (beat/digest {:name "Mvule" :shares [{:name "math" :access :rw}]}))
      "a share more or less moves it"))


(deftest a-beat-round-trips-and-nothing-else-is-one
  (is (= a-beat (beat/decode circle-key (beat/encode circle-key a-beat))))
  (is (= "041f9311d22b5a0e96e6480f30ef354675798a778ceb9ff09a4c6cd974d5cdb9"
         (:sig (read-string (beat/encode circle-key a-beat))))
      "the wire format, frozen")
  (is (nil? (beat/decode circle-key (beat/encode circle-key (dissoc a-beat :hash)))) "no hash")
  (is (nil? (beat/decode "cafe" (beat/encode circle-key a-beat))) "another circle's key")
  (is (nil? (beat/decode circle-key (pr-str (assoc a-beat :sig "00")))) "a wrong signature")
  (is (nil? (beat/decode circle-key (pr-str a-beat))) "no signature")
  (is (nil? (beat/decode circle-key "{:id \"x\"")) "noise")
  (is (nil? (beat/decode circle-key (beat/encode circle-key (assoc a-beat :id 7)))) "an id that is not a string"))
