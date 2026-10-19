(ns ujima.desktop.guard-test
  (:require [clojure.test :refer [deftest is]]
            [ujima.desktop.app   :as app]
            [ujima.desktop.guard :as guard]
            [ujima.linux.memory  :as memory]
            [ujima.linux.systemd :as systemd]))


(deftest the-victim-is-the-biggest-app-not-on-screen
  (let [apps [{:app-id :write :dirs ["/w"] :bytes 100}
              {:app-id :godot :dirs ["/g"] :bytes 600}
              {:app-id :web   :dirs ["/b"] :bytes 200}]]
    (is (= :web   (:app-id (guard/victim apps :godot))) "the app on screen is spared")
    (is (= :godot (:app-id (guard/victim apps :write))))
    (is (= :godot (:app-id (guard/victim apps nil))) "nothing on screen: the biggest")
    (is (= :godot (:app-id (guard/victim [{:app-id :godot :dirs ["/g"] :bytes 600}] :godot)))
        "alone on screen, it is what holds the machine down")
    (is (nil? (guard/victim [] :godot)) "no app, nothing to do")))


(deftest after-a-kill-the-verdict-is-what-happened-since-it
  (let [kill {:at 0 :total 0}
        s    1000000]                                   ; µs of stall per second
    (is (true? (guard/thrashing? nil 0 {:avg10 16.0 :total 0} 15.0)) "no kill yet: the average")
    (is (false? (guard/thrashing? kill 4000 {:avg10 40.0 :total (* 4 s)} 15.0))
        "4 s in: the teardown and the refaults are still settling")
    (is (true? (guard/thrashing? kill 6000 {:avg10 40.0 :total (* 6 s)} 15.0))
        "6 s of solid stall since the kill: it did not help")
    (is (false? (guard/thrashing? kill 6000 {:avg10 40.0 :total (quot s 10)} 15.0))
        "6 s with 2 % stall since the kill: it helped, the average just lags")
    (is (true? (guard/thrashing? kill 25000 {:avg10 16.0 :total 0} 15.0))
        "past the measuring window the average counts again")
    (is (false? (guard/thrashing? kill 25000 {:avg10 14.0 :total (* 25 s)} 15.0)))))


(deftest one-kill-takes-every-cgroup-and-the-next-event-waits-for-a-measurement
  (let [killed* (atom [])]
    (with-redefs [memory/reading         (constantly {:avg10 18.0 :total 0})
                  memory/app-scopes      (constantly [{:app-id :godot :dirs ["/g" "/g-stray"] :bytes 600}
                                                      {:app-id :web   :dirs ["/b"] :bytes 200}])
                  app/current-apps-state (constantly {:current {:id :web}})
                  systemd/kill-cgroup!   (fn [dir] (swap! killed* conj dir))]
      (guard/init!)
      (guard/relieve! {:full 18.0 :limit 15.0})
      (guard/relieve! {:full 18.0 :limit 15.0})
      (is (= ["/g" "/g-stray"] @killed*)))))


(deftest under-the-line-at-decision-time-nothing-dies
  (let [killed* (atom [])]
    (with-redefs [memory/reading         (constantly {:avg10 3.0 :total 0})
                  memory/app-scopes      (constantly [{:app-id :godot :dirs ["/g"] :bytes 600}])
                  app/current-apps-state (constantly {:current nil})
                  systemd/kill-cgroup!   (fn [dir] (swap! killed* conj dir))]
      (guard/init!)
      (guard/relieve! {:full 18.0 :limit 15.0})
      (is (= [] @killed*) "the event was stale; the fresh reading decides"))))
