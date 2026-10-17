(ns ujima.linux.systemd-test
  (:require [clojure.test :refer [deftest is]]
            [lib.shell           :as shell]
            [ujima.linux.systemd :as systemd]))


(deftest id-of-unit-recovers-the-app-id
  (is (= :draw (systemd/id-of-unit "ujima-app-draw-1783689762704.scope")))
  (is (= :onlyoffice (systemd/id-of-unit "ujima-app-onlyoffice-42.scope"))
      "the launch-unique suffix is stripped, the id kept whole")
  (is (= :draw (systemd/id-of-unit
                 "0::/user.slice/user-1001.slice/user@1001.service/app.slice/ujima-app-draw-9.scope"))
      "works on a cgroup path too")
  (is (nil? (systemd/id-of-unit "session-1.scope")) "someone else's scope is not ours")
  (is (nil? (systemd/id-of-unit "")) "empty is nobody's"))


(deftest spawn-scoped-shields-and-caps
  (let [argv* (atom nil)
        after (fn [k] (drop-while (complement #{k}) @argv*))]
    (with-redefs [shell/sh        (fn [_opts & args] (reset! argv* (vec args)) nil)
                  systemd/ram-mib (constantly 4000)]
      (systemd/spawn-scoped! :draw ["tuxpaint" "--fullscreen"] "/x" nil)
      (is (some #{"--property=MemoryMax=2000M"} @argv*) "half of RAM")
      (is (some #{"--property=MemorySwapMax=1000M"} @argv*) "a quarter of RAM in swap")
      (is (some #{"--property=OOMPolicy=kill"} @argv*) "one OOM-killed process takes the app")
      (is (= [:choom :-n "800" "--" :setpriv :--no-new-privs "tuxpaint" "--fullscreen"] (after :choom))
          "the app enters the sacrifice tier, then drops privileges, then runs")
      (systemd/spawn-scoped! :console ["ujima-console"] "/x" {:privileged? true})
      (is (= [:choom :-n "800" "--" "ujima-console"] (after :choom))
          "privileged skips setpriv, never the score"))))
