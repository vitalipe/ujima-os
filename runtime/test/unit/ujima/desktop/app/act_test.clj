(ns ujima.desktop.app.act-test
  "Who may elevate."
  (:require [clojure.test :refer [deftest is]]
            [lib.shell           :as shell]
            [ujima.linux.i3      :as i3]
            [ujima.linux.systemd :as systemd]
            [ujima.desktop.app.act :as act]))


(defn- an-app [id dir] {:id id :kind :exec :exec ["sh" "run.sh"] :dir dir})


(defn- launch-opts
  "run! APP against a stubbed world; the opts its scope would be launched with."
  [app]
  (let [opts* (atom nil)]
    (with-redefs [i3/switch-workspace!  (fn [_] nil)
                  systemd/active?       (constantly false)
                  systemd/spawn-scoped! (fn [_id _exec _dir opts] (reset! opts* opts))]
      (act/init! {})
      (act/run! app [])
      @opts*)))


(deftest the-console-and-the-trial-screen-may-elevate
  (doseq [id [:console :tryboot]]
    (is (:privileged? (launch-opts (an-app id (str "/ujima/apps/" (name id)))))
        "both are the console program, and both drive `ujimactl upgrade`")))


(deftest every-other-app-is-launched-without-it
  (is (not (:privileged? (launch-opts (an-app :draw "/ujima/apps/draw"))))))


(deftest the-grant-follows-the-id-wherever-the-app-came-from
  (is (:privileged? (launch-opts (an-app :console "/ujima/storage/apps/console")))
      "deliberate for now — a console built outside the image can be dropped in and elevate"))


;; --- what the flag actually does to the command line ------------------------

(defn- argv [opts]
  (let [seen* (atom nil)]
    (with-redefs [shell/sh (fn [_opts & args] (reset! seen* (vec args)))]
      (systemd/spawn-scoped! :console ["sh" "run.sh"] "/ujima/apps/console" opts)
      @seen*)))


(deftest a-plain-scope-carries-the-no-new-privs-prefix
  (is (= ["setpriv" "--no-new-privs" "sh" "run.sh"]
         (mapv str (take-last 4 (argv {}))))))


(deftest a-privileged-scope-drops-it-and-the-flag-never-reaches-the-process
  (is (= ["--" "sh" "run.sh"] (mapv str (take-last 3 (argv {:privileged? true}))))
      "nothing between the -- and the app's own argv")
  (let [seen* (atom nil)]
    (with-redefs [shell/sh (fn [opts & _] (reset! seen* opts))]
      (systemd/spawn-scoped! :console ["sh"] "/d" {:privileged? true :extra-env {"K" "v"}}))
    (is (nil? (:privileged? @seen*)) "ours, stripped")
    (is (= {"K" "v"} (:extra-env @seen*)) "theirs, kept")))
