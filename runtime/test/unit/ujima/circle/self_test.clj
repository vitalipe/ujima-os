(ns ujima.circle.self-test
  "What this machine offers: the share verbs, what is served now, the hash, the arrows."
  (:require [clojure.test :refer [deftest is]]
            [ujima.circle.beat :as beat]
            [ujima.circle.self :as self]))


(def ^:private stick   [:usb "6962-5E15"])
(def ^:private math    {:place stick :path "Grade 5/math" :name "math" :access :ro})
(def ^:private mounted {:kind :usb :name "6962-5E15" :state :mounted :storage "/ujima/run/storage/6962-5E15"})

(defn- refused [f] (try (f) nil (catch clojure.lang.ExceptionInfo e (:error (ex-data e)))))


(deftest share-keeps-the-folder-canonically-under-its-own-name
  (self/init!)
  (is (= math (self/share! {:place stick :path "./Grade 5//math/"}))
      "empty and dot segments go; the folder's name; read-only unless asked")
  (is (= [math] (:shares (self/snapshot)))))


(deftest sharing-a-folder-again-changes-what-is-given-and-keeps-the-rest
  (self/init!)
  (self/share! math)
  (let [renamed (assoc math :name "Maths" :access :rw)]
    (is (= renamed (self/share! {:place stick :path "Grade 5/math" :name "Maths" :access :rw})))
    (is (= [renamed] (:shares (self/snapshot))) "one entry per folder, not two")
    (is (= renamed (self/share! {:place stick :path "Grade 5/math"}))
        "re-added with nothing said: the name and access it has, not the defaults")))


(deftest a-shares-name-is-unique
  (self/init!)
  (self/share! math)
  (is (= :shares/name-taken (refused #(self/share! {:place stick :path "Photos" :name "math"}))))
  (is (= "math" (:name (self/share! {:place stick :path "Grade 5/math"})))
      "the folder's own entry is not a clash with itself"))


(deftest a-path-out-of-the-place-is-malformed
  (self/init!)
  (doseq [path ["../etc" "a/../../etc" "/etc" ".." "."]]
    (is (= :request/malformed (refused #(self/share! {:place stick :path path}))) path))
  (is (= "a..b" (:name (self/share! {:place stick :path "x/a..b"}))) "dots inside a name are a name"))


(deftest unshare-finds-the-folder-or-refuses
  (self/init!)
  (self/share! math)
  (is (= :shares/unknown-share (refused #(self/unshare! {:place stick :path "Photos"}))))
  (is (= {:removed [stick "Grade 5/math"]} (self/unshare! {:place stick :path "Grade 5/math/"})))
  (is (= [] (:shares (self/snapshot)))))


(deftest what-is-published-is-the-name-and-the-shares-whose-place-is-mounted-by-name
  (self/init!)
  (self/converge-settings! {[:system :name] {:effective "Mvule"}} nil)
  (self/share! {:place stick :path "zed" :access :rw})
  (self/share! {:place stick :path "apple"})
  (self/share! {:place [:local "storage"] :path "Books"})
  (is (= {:name "Mvule" :published [] :hash (beat/digest {:name "Mvule" :shares []})}
         (dissoc (self/snapshot) :shares))
      "no place heard of yet: nothing is served, and the hash says so")
  (self/converge-storage! [mounted] nil)
  (let [served [{:name "apple" :access :ro} {:name "zed" :access :rw}]]
    (is (= served (:published (self/snapshot)))
        "by name, whatever order they were shared in — the hash must not move when nothing did")
    (is (= (beat/digest {:name "Mvule" :shares served}) (:hash (self/snapshot))))))


(deftest every-write-reaches-the-targets-with-next-and-prev
  (self/init!)
  (let [heard (atom [])]
    (self/on-converge! (fn [next prev] (swap! heard conj [(count (:published prev)) (count (:published next))])))
    (self/share! math)
    (self/converge-storage! [mounted] nil)
    (self/unshare! math)
    (is (= [[0 0] [0 1] [1 0]] @heard) "a verb, the storage arrow, a verb: one converge each")))
