(ns ujima.events.tryboot-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [babashka.fs :as fs]
            [ujima.control :as control]
            [ujima.desktop.app :as app]
            [ujima.desktop.app.catalog :as catalog]
            [ujima.device.ab :as ab]
            [ujima.events.tryboot :as tryboot]))


(defrecord FakeRuntime [trial]
  ab/UjimaBootRuntime
  (try-boot!    [_] nil)
  (running-slot [_] :b)
  (trial-boot?  [_] trial))


(use-fixtures :each (fn [run]
                      (let [dir (str (fs/create-temp-dir))]
                        (control/init! {:storage dir :tmp dir})
                        (control/settings! :circle [:circle :token] "abc"))
                      (run)))


(deftest a-trial-boot-hands-the-app-this-machines-key-then-runs-it
  (let [calls* (atom [])]
    (with-redefs [catalog/merge-app! (fn [id changes] (swap! calls* conj [:update id changes]))
                  app/run!           (fn [id] (swap! calls* conj [:run id]))]
      (tryboot/init! (->FakeRuntime true))
      (is (true? (tryboot/trial?)))
      (tryboot/open!)
      (is (= [[:update :tryboot {:hidden false :env {"UJIMA_CIRCLE_TOKEN" "abc"}}] [:run :tryboot]]
             @calls*)
          "key and pin before the run — the page signs with the machine's own key"))))


(deftest a-committed-boot-leaves-the-app-hidden
  (let [calls* (atom [])]
    (with-redefs [catalog/merge-app! (fn [& _] (swap! calls* conj :update))
                  app/run!           (fn [& _] (swap! calls* conj :run))]
      (tryboot/init! (->FakeRuntime false))
      (tryboot/open!)
      (is (false? (tryboot/trial?)))
      (is (= [] @calls*)))))


(deftest a-host-without-a-disk-is-never-a-trial
  (tryboot/init! nil)
  (is (false? (tryboot/trial?))))


(deftest a-missing-app-is-an-error-line-not-a-dead-boot
  (with-redefs [catalog/merge-app! (fn [& _] (throw (ex-info "unknown app" {})))]
    (tryboot/init! (->FakeRuntime true))
    (is (nil? (tryboot/open!)))))
