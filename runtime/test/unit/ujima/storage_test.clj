(ns ujima.storage-test
  "The pure half: marker sweeping and state derivation. Mount effects are hardware, not
   unit tests."
  (:require [clojure.test  :refer [deftest is]]
            [babashka.fs   :as fs]
            [cheshire.core :as json]
            [lib.task      :as task]
            [lib.task.flow :refer [flow]]
            [ujima.linux.disk.mount :as mount]
            [ujima.linux.smb :as smb]
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
           (->entry (assoc facts :kind :usb :name "U") t)))))


(deftest an-invalid-entry-carries-why
  (let [t (doto (flow :t (throw (ex-info "mount: unknown filesystem type 'exfat'" {})))
            (task/run!!))
        e (->entry {:uuid "U" :kind :usb :name "U"} t)]
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
      (storage/handle-event! {:type :storage/partitions :partitions {}})
      (is (= [entry] (storage/snapshot)) "a udev pass must not drop it"))))


(deftest a-mount-point-without-a-partition-is-released
  (let [mounts   (str (fs/create-temp-dir))
        released (atom [])]
    (fs/create-dirs (fs/path mounts "GONE-UUID"))
    (with-redefs [mount/umount-lazy! (fn [mnt] (swap! released conj (str mnt)) true)]
      (storage/init! {:mounts-dir mounts :places []})
      (is (= [(str (fs/path mounts "GONE-UUID"))] @released) "lazily, device or not")
      (is (not (fs/exists? (fs/path mounts "GONE-UUID"))) "and its point is gone")
      (storage/handle-event! {:type :storage/partitions :partitions {}})
      (is (= 1 (count @released)) "the next pass finds nothing to release"))))


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


;; --- a peer's root, on request ------------------------------------------------

(defn- settle [pred]
  (let [deadline (+ (System/currentTimeMillis) 3000)]
    (while (and (not (pred)) (< (System/currentTimeMillis) deadline))
      (Thread/sleep 10))
    (pred)))

(defn- peer-entries [] (filterv #(= :peer (:kind %)) (storage/snapshot)))

(def ^:private a-request
  {:peer "87f6" :access :rw :host "10.0.0.5" :credentials "/run/smb/credentials"})


(deftest a-peer-root-is-mounted-on-request-and-once
  (let [peers  (str (fs/create-temp-dir))
        mounts (atom [])]
    (with-redefs [smb/mount! (fn [& args] (swap! mounts conj (into [] args)))]
      (storage/init! {:mounts-dir (str (fs/create-temp-dir)) :peers-dir peers :places []})
      (storage/mount! a-request)
      (is (settle #(= :mounted (:state (first (peer-entries))))))
      (is (= [{:kind :peer :name "87f6/rw" :peer "87f6" :access :rw :host "10.0.0.5" :state :mounted
               :mount (str peers "/87f6/rw") :storage (str peers "/87f6/rw")}]
             (peer-entries))
          "an entry like a stick's; the credentials path stays out of it")
      (is (= [["/run/smb/credentials" "10.0.0.5" :rw (str peers "/87f6/rw")]] @mounts)
          "all the mount needs came in the request")
      (storage/mount! a-request)
      (storage/handle-event! {:type :storage/partitions :partitions {}})
      (is (= 1 (count @mounts)) "asked again while mounted, or a udev pass: left alone"))))


(deftest a-failed-peer-mount-is-tried-again-only-when-asked
  (let [mounts (atom 0)]
    (with-redefs [smb/mount! (fn [& _] (swap! mounts inc) (throw (ex-info "mount error(113): No route to host" {})))]
      (storage/init! {:mounts-dir (str (fs/create-temp-dir)) :peers-dir (str (fs/create-temp-dir)) :places []})
      (storage/mount! a-request)
      (is (settle #(= :invalid (:state (first (peer-entries))))))
      (is (= "mount error(113): No route to host" (:reason (first (peer-entries)))))
      (storage/handle-event! {:type :storage/partitions :partitions {}})
      (is (= 1 @mounts) "a pass never retries on its own")
      (storage/mount! a-request)
      (is (settle #(= 2 @mounts)) "the request is the retry"))))


(deftest a-peer-point-without-an-entry-is-released
  (let [peers    (str (fs/create-temp-dir))
        released (atom [])]
    (fs/create-dirs (fs/path peers "gone" "rw"))
    (with-redefs [mount/umount-lazy! (fn [mnt] (swap! released conj (str mnt)) true)]
      (storage/init! {:mounts-dir (str (fs/create-temp-dir)) :peers-dir peers :places []})
      (is (= [(str (fs/path peers "gone" "rw"))] @released) "a point left from the last run")
      (is (not (fs/exists? (fs/path peers "gone"))) "its point and the peer's dir are gone"))))


(deftest a-peer-gone-from-the-circle-takes-its-mounts
  (let [peers    (str (fs/create-temp-dir))
        released (atom [])
        other    {:peer "c0ffee" :access :ro :host "10.0.0.6" :credentials "/run/smb/credentials"}]
    (with-redefs [smb/mount!         (fn [_ _ _ mnt] (fs/create-dirs mnt))
                  mount/umount-lazy! (fn [mnt] (swap! released conj (str mnt)) true)]
      (storage/init! {:mounts-dir (str (fs/create-temp-dir)) :peers-dir peers :places []})
      (storage/mount! a-request)
      (storage/mount! other)
      (is (settle #(= [:mounted :mounted] (mapv :state (peer-entries)))))
      (storage/handle-event! {:type :circle/peers :peers #{"87f6" "c0ffee"}})
      (storage/handle-event! {:type :storage/partitions :partitions {}})
      (is (= 2 (count (peer-entries))) "both present, or a udev pass: nothing moves")
      (storage/handle-event! {:type :circle/peers :peers #{"87f6"}})
      (is (= ["87f6/rw"] (mapv :name (peer-entries))) "the absent peer's mount is gone")
      (is (= [(str (fs/path peers "c0ffee" "ro"))] @released) "and its point released")
      (is (not (fs/exists? (fs/path peers "c0ffee"))))
      (is (fs/exists? (fs/path peers "87f6" "rw"))))))


(deftest an-event-of-no-known-type-is-refused
  (storage/init! {:mounts-dir (str (fs/create-temp-dir)) :peers-dir (str (fs/create-temp-dir)) :places []})
  (is (thrown? IllegalArgumentException (storage/handle-event! {:type :storage/bogus}))))
