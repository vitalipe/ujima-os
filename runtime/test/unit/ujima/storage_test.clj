(ns ujima.storage-test
  "The pure half: marker sweeping and state derivation. Mount effects are hardware, not
   unit tests."
  (:require [clojure.test  :refer [deftest is]]
            [babashka.fs   :as fs]
            [cheshire.core :as json]
            [lib.task      :as task]
            [lib.task.flow :refer [flow]]
            [ujima.linux.disk.mount :as mount]
            [ujima.storage :as storage]))


(def ^:private sweep      #'storage/sweep)
(def ^:private task->state #'storage/task->state)
(def ^:private ->entry    #'storage/->entry)


(defn- a-mount-with [filename content]
  (let [dir (str (fs/create-temp-dir))]
    (when filename
      (fs/create-dirs (fs/path dir "ujima"))
      (spit (str (fs/path dir "ujima" filename)) content))
    dir))


;; --- sweep ------------------------------------------------------------------

(deftest a-marker-is-reported-with-its-parsed-value
  (is (= {:circle/secret {:key "abc" :circle "room-1"}}
         (sweep (a-mount-with "circle.json"
                              (json/generate-string {:key "abc" :circle "room-1"}))))))


(deftest a-pack-registration-is-a-token-like-any-other
  (is (= {:ujima/pack {:pack "cool-game.pack" :label "Cool Game"}}
         (sweep (a-mount-with "install.json"
                              (json/generate-string {:pack  "cool-game.pack"
                                                     :label "Cool Game"}))))
      "the token is the data — whether the pack path exists is the install verb's question"))


(deftest a-stick-without-the-ujima-dir-reports-nothing
  (is (= {} (sweep (a-mount-with nil nil)))
      "no ujima/ dir — the one stat that gates everything")
  (let [dir (str (fs/create-temp-dir))]
    (spit (str (fs/path dir "circle.json")) "{}")
    (is (= {} (sweep dir))
        "marker names at the mount ROOT are not markers — the clean cut from v0.4")))


(deftest an-unreadable-marker-is-logged-and-skipped
  (is (= {} (sweep (a-mount-with "circle.json" "{not json")))
      "junk fails the shape gate — the warn log is the loudness, absence the contract"))


(deftest a-marker-with-the-wrong-shape-is-logged-and-skipped
  (is (= {} (sweep (a-mount-with "circle.json"
                                 (json/generate-string {:circle "room-1"}))))
      "parses fine, but no :key — nothing downstream ever sees a half-token")
  (is (= {} (sweep (a-mount-with "install.json"
                                 (json/generate-string {:pack 42}))))
      "a pack registration whose path is not a string"))


(deftest an-absurd-marker-is-not-slurped
  (let [dir (a-mount-with "circle.json" (apply str (repeat 70000 "x")))]
    (is (= {} (sweep dir))
        "over the cap the read yields nil, nil fails the shape — a 4GB file never reaches the heap")))


(deftest a-directory-named-like-a-marker-is-not-a-marker
  (let [dir (str (fs/create-temp-dir))]
    (fs/create-dirs (fs/path dir "ujima" "circle.json"))
    (is (= {} (sweep dir)))))


;; --- state derivation -------------------------------------------------------

(deftest state-comes-from-presence-plus-the-task
  (is (= :detected (task->state nil))
      "present with no task is the only state that has an action")

  (let [cold (flow :t 1)]
    (is (= :mounting (task->state cold))
        "a task exists = in flight. run!! claims on the spawned thread, so a second event
         landing in that window must NOT see the one state that starts a mount"))

  (let [done (doto (flow :t {:mount "/x" :tokens []}) (task/run!!))]
    (is (= :mounted (task->state done))))

  (let [boom (doto (flow :t (throw (ex-info "mount: no such device" {}))) (task/run!!))]
    (is (= :invalid (task->state boom)))))


(deftest a-mounted-entry-carries-the-task-result
  (let [facts {:uuid "U" :disk "sda" :fstype "vfat"}
        t     (doto (flow :t {:mount "/ujima/run/storage/U" :tokens {:circle/secret {:key "abc"}}})
                (task/run!!))]
    (is (= {:uuid "U" :disk "sda" :fstype "vfat" :kind :usb :name "U" :state :mounted
            :mount "/ujima/run/storage/U" :tokens {:circle/secret {:key "abc"}}}
           (->entry {:facts facts :task t})))))


(deftest an-invalid-entry-carries-why
  (let [t (doto (flow :t (throw (ex-info "mount: unknown filesystem type 'exfat'" {})))
            (task/run!!))
        e (->entry {:facts {:uuid "U"} :task t})]
    (is (= :invalid (:state e)))
    (is (= "mount: unknown filesystem type 'exfat'" (:reason e))
        "the reason comes off the task timeline, not a hand-maintained field")))


;; --- machine partitions -----------------------------------------------------

(def ^:private local-place
  {:kind :local :name "storage" :mount "/ujima/storage"
   :provides {:storage "files/"} :label "UJSTORE" :fstype "ext4"})

(deftest a-mounted-local-place-is-adopted
  (with-redefs [mount/mount-point? (constantly true)]
    (storage/init! {:mounts-dir (str (fs/create-temp-dir)) :places [local-place]})
    (let [entry {:kind :local :name "storage" :mount "/ujima/storage"
                 :label "UJSTORE" :fstype "ext4"
                 :storage "/ujima/storage/files" :state :mounted :tokens {}}]
      (is (= [entry] (storage/snapshot)) "observed mounted — adopted, never managed")
      (storage/handle-event! {:partitions {}})
      (is (= [entry] (storage/snapshot)) "a udev pass must not drop it"))))


(deftest an-unmounted-local-place-tells-the-truth
  (with-redefs [mount/mount-point? (constantly false)
                fs/create-dirs     (fn [_] (throw (ex-info "must not mkdir a :local" {})))]
    (storage/init! {:mounts-dir (str (fs/create-temp-dir)) :places [local-place]})
    (is (= [{:kind :local :name "storage" :label "UJSTORE" :fstype "ext4"
             :state :invalid :reason "not mounted: /ujima/storage"}]
           (storage/snapshot))
        "invalid drops mount+storage (the reason is the whole story) — and never mkdir's the black hole")))


(deftest a-session-place-is-created-and-always-ready
  (let [dir  (str (fs/path (fs/create-temp-dir) "session-root"))
        seen (atom nil)]
    (with-redefs [mount/mount-point? (fn [_] (throw (ex-info "must not check a :session" {})))
                  fs/create-dirs     (fn [d] (reset! seen (str d)))]
      (storage/init! {:mounts-dir (str (fs/create-temp-dir))
                      :places [{:kind :session :name "files" :mount dir
                                :provides {:storage "/"}}]})
      (is (= dir @seen) "its storage root is created — nothing else makes it")
      (is (= [{:kind :session :name "files" :mount dir :storage dir
               :state :mounted :tokens {}}]
             (storage/snapshot))
          "ephemeral by contract — no check, always ready"))))
