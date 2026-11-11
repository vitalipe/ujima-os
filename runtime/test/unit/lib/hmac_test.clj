(ns lib.hmac-test
  "The signed tuple, pinned. GOLDEN vectors, not round-trips: a round-trip passes
   even when the format changes on both sides at once."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [lib.hmac :as hmac]))


(def ^:private signing-key
  "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef")

(def ^:private fields ["node-a-3f9c1a" 1337 "b7c1e2f4a91d6035" 42])
(def ^:private good   "818daffeb5b64aed68a888c14f7fffdc0d7094e26d7182d9b501f1bafd4bd60f")


(deftest the-tuple-format-is-frozen
  (is (= "peer-beat-v1\nnode-a-3f9c1a\n1337\nb7c1e2f4a91d6035\n42"
         (hmac/text "peer-beat-v1" fields)))
  (is (= good (hmac/sign signing-key "peer-beat-v1" fields))))


(deftest verify-accepts-only-the-real-thing
  (is (true?  (hmac/verify signing-key "peer-beat-v1" fields good)))
  (is (false? (hmac/verify signing-key "other-v1"     fields good)) "the tag is signed")
  (is (false? (hmac/verify signing-key "peer-beat-v1" (assoc fields 3 43) good)))
  (is (false? (hmac/verify signing-key "peer-beat-v1" fields (str/replace good #"^81" "aa"))))
  (is (false? (hmac/verify "a-different-key" "peer-beat-v1" fields good)))
  (is (false? (hmac/verify signing-key "peer-beat-v1" fields nil)))
  (is (false? (hmac/verify nil "peer-beat-v1" fields good))))


(deftest a-newline-in-a-field-is-refused
  ;; it would shift every boundary after it, so two tuples could sign the same
  (is (= :signature/bad-field
         (try (hmac/sign signing-key "t" ["a\nb"])
              (catch clojure.lang.ExceptionInfo e (:error (ex-data e)))))))


(deftest nonces-are-hex-and-distinct
  (let [nonces (repeatedly 50 hmac/nonce)]
    (is (every? #(re-matches #"^[0-9a-f]{32}$" %) nonces))
    (is (= 50 (count (set nonces))))))
