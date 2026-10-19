(ns ujima.linux.systemd-test
  (:require [clojure.test :refer [deftest is]]
            [babashka.fs         :as fs]
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


;; --- the app slice: our scopes by name, a browser's self-made one by the pid we spawned

(defn- fake-cgroup! [root name]
  (let [d (fs/path root name)]
    (fs/create-dirs d)
    (str d)))


(deftest the-slice-scan-gives-a-stray-scope-back-to-its-app
  (let [root  (str (fs/create-temp-dir))
        unit* (atom nil)]
    (with-redefs [shell/sh        (fn [_ & args]
                                    (reset! unit* (some #(when (string? %) (second (re-find #"^--unit=(.+)" %))) args))
                                    {:proc :fake})
                  systemd/pid-of  (constantly "50")
                  systemd/ram-mib (constantly 4000)]
      (systemd/spawn-scoped! :web ["chromium"] "/x" nil))
    (let [unit  @unit*
          web   (fake-cgroup! root unit)
          stray (fake-cgroup! root "app-org.chromium.Chromium-50.scope")]
      (fake-cgroup! root "app-gnome-foo-77.scope")
      (fake-cgroup! root "ujima-shell.service")
      (is (re-matches #"ujima-app-web-\d+\.scope" unit) "systemd-run is given the full name")
      (is (= #{{:unit unit :app-id :web :dir web} {:unit unit :app-id :web :dir stray}}
             (set (systemd/app-cgroups root)))
          "the stray is the app's; the shell and a scope of a pid we never spawned are not")
      (is (nil? (systemd/app-cgroups (str (fs/path root "nope")))) "no slice, nothing"))))


(defn- systemctl-world
  "A fake systemctl: ACTIVE is the list-units answer, stops are collected in STOPPED*."
  [active stopped*]
  (fn [& args]
    (let [args (map str args)]
      (cond (some #{"list-units"} args) {:ok? true :out (apply str (map #(str % " loaded active running\n") active))}
            (some #{"stop"} args)       (do (swap! stopped* conj (last args)) {:ok? true :out ""})
            :else                       {:ok? true :out ""}))))


(deftest kill-cgroup-writes-the-kernel-knob
  (let [d (str (fs/create-temp-dir))]
    (systemd/kill-cgroup! d)
    (is (= "1" (slurp (str (fs/path d "cgroup.kill")))))))


(deftest a-cgroup-that-is-already-gone-is-done
  (let [d (str (fs/path (fs/create-temp-dir) "ujima-app-web-5.scope"))]
    (is (nil? (systemd/kill-cgroup! d)) "the app quit between the scan and the kill: nothing to do")
    (when-not (shell/root?)
      (let [knob (str (fs/path d "cgroup.kill"))]
        (fs/create-dirs d)
        (spit knob "")
        (fs/set-posix-file-permissions knob "r--------")
        (is (thrown? java.io.FileNotFoundException (systemd/kill-cgroup! d))
            "a knob that is there and refuses the write is still an error")))))


(deftest stop-takes-the-stray-with-the-scope
  (let [stopped* (atom []) killed* (atom [])
        cgroups  [{:unit "ujima-app-web-5.scope" :app-id :web :dir "/s/ujima-app-web-5.scope"}
                  {:unit "ujima-app-web-5.scope" :app-id :web :dir "/s/app-org.chromium.Chromium-50.scope"}
                  {:unit "ujima-app-draw-6.scope" :app-id :draw :dir "/s/ujima-app-draw-6.scope"}]]
    (with-redefs [shell/sh?            (systemctl-world ["ujima-app-web-5.scope"] stopped*)
                  systemd/app-cgroups  (constantly cgroups)
                  systemd/kill-cgroup! (fn [d] (swap! killed* conj d))]
      (systemd/stop! :web)
      (is (= ["ujima-app-web-5.scope"] @stopped*) "the scope is stopped; draw untouched")
      (is (= ["/s/app-org.chromium.Chromium-50.scope"] @killed*)
          "what the browser moved out of it is killed outright"))))


(deftest reap-finishes-only-an-orphaned-stray
  (let [stopped* (atom []) killed* (atom [])
        cgroups  [{:unit "ujima-app-web-5.scope" :app-id :web :dir "/s/app-org.chromium.Chromium-50.scope"}
                  {:unit "ujima-app-web-9.scope" :app-id :web :dir "/s/ujima-app-web-9.scope"}
                  {:unit "ujima-app-web-9.scope" :app-id :web :dir "/s/app-org.chromium.Chromium-90.scope"}]]
    (with-redefs [shell/sh?            (systemctl-world ["ujima-app-web-9.scope"] stopped*)
                  systemd/app-cgroups  (constantly cgroups)
                  systemd/kill-cgroup! (fn [d] (swap! killed* conj d))]
      (systemd/reap! :web)
      (is (= ["/s/app-org.chromium.Chromium-50.scope"] @killed*)
          "scope 5 is dead, its stray goes; the relaunched 9 and its stray live on")
      (is (= [] @stopped*) "nothing of ours is stopped"))))
