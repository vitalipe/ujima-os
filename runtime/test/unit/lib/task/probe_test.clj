(ns lib.task.probe-test
  (:require [clojure.test :refer [deftest is]]
            [lib.task       :as task]
            [lib.task.flow  :refer [flow]]
            [lib.task.probe :as probe]))


(defn- settle [pred]
  (let [deadline (+ (System/currentTimeMillis) 3000)]
    (while (and (not (pred)) (< (System/currentTimeMillis) deadline))
      (Thread/sleep 10))
    (pred)))


(deftest a-probe-is-cold-and-each-run-is-one-attempt
  (let [asked (atom [])
        p     (probe/->probe (fn [n] (flow :count (swap! asked conj n) (* n 10))))]
    (is (= :new (probe/probe->state p)))
    (is (nil? (probe/probe->task p)))
    (is (nil? @p))
    (is (= [] @asked) "made, not run")

    (probe/run!! p 1)
    (is (= :done (probe/probe->state p)))
    (is (= 10 @p))
    (is (task/finished? (probe/probe->task p)) "the attempt is a task")

    (probe/run!! p 2)
    (is (= 20 @p))
    (is (= [1 2] @asked) "two asks, two attempts")))


(deftest the-last-answer-holds-while-the-next-attempt-runs-or-fails
  (let [gate (promise)
        p    (probe/->probe (fn [mode]
                              (flow :ask
                                (case mode
                                  :ok   :answer
                                  :slow (do @gate :late)
                                  :bad  (error! :probe/bad "no")))))]
    (probe/run!! p :ok)
    (is (= :answer @p))

    (let [attempt (probe/run! p :slow)]
      (is (= :running (probe/probe->state p)))
      (is (= :answer @p) "the old answer, while the new attempt runs")
      (deliver gate :go)
      (is (settle #(task/finished? attempt)))
      (is (= :late @p)))

    (probe/run!! p :bad)
    (is (= :error (probe/probe->state p)))
    (is (= :late @p) "a failed attempt leaves the last answer in place")

    (probe/run!! p :ok)
    (is (= :done (probe/probe->state p)))
    (is (= :answer @p))))
