(ns schema.ujima.storage-test
  (:require [clojure.test :refer [deftest is]]
            [schema.ujima.storage :as schema]))


(deftest the-label-routes-to-a-convention
  (is (= {:storage "files/" :tokens "ujima"} (schema/convention "UJIMAOS1")))
  (is (= (schema/convention "UJIMAOS1") (schema/convention "ujimaos1"))
      "ext4 labels can arrive lowercase"))


(deftest everything-else-is-foreign
  (is (= schema/foreign (schema/convention nil)))
  (is (= schema/foreign (schema/convention "MUSIC")))
  (is (= schema/foreign (schema/convention "UJSTORE"))
      "machine labels are internal — not part of the stick contract")
  (is (= schema/foreign (schema/convention "UJIMAOS"))    "no version, no convention")
  (is (= schema/foreign (schema/convention "UJIMAOS1X"))  "anchored — no prefix match")
  (is (= schema/foreign (schema/convention "UJIMAOS99"))  "a stick newer than this machine"))
