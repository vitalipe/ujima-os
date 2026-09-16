(ns ujima.pack-test
  (:require [clojure.test :refer [deftest is]]
            [ujima.pack :as pack])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))


(deftest copying-reports-the-running-total-and-the-end
  (let [bytes (byte-array (* 3 1024 1024) (byte 7))
        in    (ByteArrayInputStream. bytes)
        out   (ByteArrayOutputStream.)
        seen* (atom [])]
    (is (= (count bytes) (pack/copy-counting! in out #(swap! seen* conj %))))
    (is (= (count bytes) (.size out)) "every byte reaches the sink")
    (is (= [(count bytes)] @seen*) "under the pulse size only the end is reported")))


(deftest copying-nothing-still-reports-the-end
  (let [seen* (atom [])]
    (is (= 0 (pack/copy-counting! (ByteArrayInputStream. (byte-array 0))
                                  (ByteArrayOutputStream.)
                                  #(swap! seen* conj %))))
    (is (= [0] @seen*))))
