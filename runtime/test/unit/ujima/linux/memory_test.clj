(ns ujima.linux.memory-test
  (:require [clojure.test :refer [deftest is]]
            [babashka.fs        :as fs]
            [ujima.linux.memory :as memory]))


(def ^:private psi
  "some avg10=1.50 avg60=0.40 avg300=0.10 total=4535\nfull avg10=17.25 avg60=3.00 avg300=0.70 total=4138\n")


(deftest the-reading-is-the-full-line
  (is (= {:avg10 17.25 :total 4138} (memory/parse-full psi)))
  (is (nil? (memory/parse-full "")) "no PSI, no reading"))



(defn- fake-scope! [root name anon swap]
  (let [d (fs/path root name)]
    (fs/create-dirs d)
    (spit (str (fs/path d "memory.stat")) (str "anon " anon "\nfile 99\n"))
    (spit (str (fs/path d "memory.swap.current")) (str swap "\n"))
    (str d)))


(deftest app-scopes-report-what-a-kill-gives-back
  (let [root  (str (fs/create-temp-dir))
        godot (fake-scope! root "ujima-app-godot-11.scope" 600 50)
        web   (fake-scope! root "ujima-app-web-12.scope" 200 0)
        stray (fake-scope! root "app-org.chromium.Chromium-70.scope" 40 2)
        unit  (fn [u d] {:unit u :app-id (keyword (second (re-find #"ujima-app-(\w+)-" u))) :dir d})]
    (is (= #{{:app-id :godot :dirs [godot] :bytes 650}
             {:app-id :web :dirs [web stray] :bytes 242}}
           (set (memory/app-scopes [(unit "ujima-app-godot-11.scope" godot)
                                    (unit "ujima-app-web-12.scope" web)
                                    (unit "ujima-app-web-12.scope" stray)])))
        "anon + swap over every cgroup of the app")
    (is (= [] (memory/app-scopes nil)) "no slice, no apps")))

