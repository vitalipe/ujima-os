(ns ujima.storage
  "Removable partitions as observed state. Several threads write — the listeners, a verb, a
   mount that just finished — so a pass runs whole under one lock: fold the event in, start
   what is not started, push. The mount itself runs outside the lock, so the critical section
   is a fold and a derive, never the 40ms of mounting.

   Storage sweeps for markers and reports what it finds; it never interprets them.

   A peer's Samba root is mounted on request (`mount!`), with everything the mount needs in
   the request; it is an entry like a stick's, under /ujima/run/peer/<peer id>/<rw|ro>, and
   gone with the peer (the circle's :peers event)."
  (:require [babashka.fs            :as fs]
            [cheshire.core          :as json]
            [lib.io                 :as io]
            [lib.task               :as task]
            [lib.task.flow          :refer [flow]]
            [lib.task.timeline      :as timeline]
            [malli.core             :as m]
            [malli.error            :as me]
            [schema.ujima.storage   :as schema]
            [ujima.log              :as log]
            [ujima.linux.disk.mount :as mount]
            [ujima.linux.smb        :as smb]))


(def ^:private max-marker-bytes 65536)
(def ^:private default-mounts-dir "/ujima/run/storage")
(def ^:private default-peers-dir  "/ujima/run/peer")


(defonce ^:private partitions* (atom {}))   ; uuid -> {:facts f :task t}
(defonce ^:private peers*      (atom {}))   ; "<peer id>/<rw|ro>" -> {:facts f :credentials path :task t}
(defonce ^:private builtin-places* (atom []))  ; declared in config, resolved at init — always ahead of removable partitions
(defonce ^:private prev*       (atom nil))  ; last projection, the prev of (next prev)
(defonce ^:private cfg*        (atom {}))   ; :mounts-dir by init!, :targets by on-converge!
(defonce ^:private lock        (Object.))   ; every write path holds this


(defn- mount-of      [uuid] (str (fs/path (:mounts-dir @cfg*) uuid)))
(defn- peer-mount-of [name] (str (fs/path (:peers-dir @cfg*) name)))


(defn- release-mount!
  "Give a mount point back: lazy so it works with the device already gone."
  [mnt]
  (try
    (mount/umount-lazy! mnt)
    (fs/delete-if-exists mnt)
    (catch Throwable e
      (log/warn "storage: could not release mount" {:mount mnt :error (ex-message e)}))))


;; --- markers (read once, here, where the size is bounded) -------------------

(defn- marker-value [path]
  (try
    (if (<= (fs/size path) max-marker-bytes)
      (json/parse-string (io/slurp-text (str path) nil) true)
      (do (log/warn "storage: marker too large — value ignored" {:path (str path)}) nil))
    (catch Throwable e
      (log/warn "storage: unreadable marker" {:path (str path) :error (ex-message e)})
      nil)))


(defn- valid-token?
  "The shape gate: junk json, an over-cap read (both arrive as nil) and wrong shapes
   all fail the same way — logged and skipped. The log is the loudness, absence the
   contract; nothing downstream ever sees a half-token."
  [type value path]
  (let [shape (schema/shapes type)]
    (or (m/validate shape value)
        (do (log/warn "storage: invalid token — skipped"
                      {:type type :path (str path)
                       :reason (me/humanize (m/explain shape value))})
            false))))


(defn- sweep
  "Stat the known names under the ujima/ dir — one stat gates everything, and never
   enumerate. Values are validated against their type's shape. Tokens are read only
   here, from physically-present media: no other kind of place may provide :tokens."
  [mnt]
  (let [dir (fs/path mnt schema/dir)]
    (if (fs/directory? dir)
      (into {} (for [[filename type] schema/markers
                     :let  [path (fs/path dir filename)]
                     :when (fs/regular-file? path)
                     :let  [value (marker-value path)]
                     :when (valid-token? type value path)]
                 [type value]))
      {})))


;; --- state derivation -------------------------------------------------------

(defn- task->state [t]
  (case (some-> t task/task->state)
    nil             :detected
    (:new :running) :mounting   ; created or claimed — either way it is in flight
    :done           :mounted
    :invalid))


(defn- task-result [t]
  (:payload (timeline/timeline->last-of (task/task->timeline t) (:id t))))


(defn- ->entry
  "FACTS (with :kind and :name) at the state TASK gives, plus what the task brought."
  [facts task]
  (let [state (task->state task)]
    (case state
      :mounted (merge facts {:state state} (task-result task))
      :invalid (let [{:keys [error message]} (task-result task)]
                 (assoc facts :state state :reason (or (ex-message error) message)))
      (assoc facts :state state))))


(defn- entries [known kind]
  (for [[name {:keys [facts task]}] (sort-by key known)]
    (->entry (assoc facts :kind kind :name name) task)))

(defn- projection []
  (-> (vec @builtin-places*)
      (into (entries @partitions* :usb))
      (into (entries @peers* :peer))))


;; --- the write path (serialized) --------------------------------------------

(declare handle-event!)


(defn- provision-root [backing sub]
  (if (contains? #{nil "" "/"} sub) backing (str (fs/path backing sub))))


;; rw where repair exists and the semantics are ours; ro where they are not — ntfs3
;; has no real linux fsck (ntfsfix only clears the dirty flag), and an unknown
;; filesystem mounts ro because readable beats invisible.
(def ^:private mount-opts
  {"vfat"  ["rw" "uid=ujima" "gid=ujima" "utf8" "flush" "nosuid" "nodev" "noexec"]
   "exfat" ["rw" "uid=ujima" "gid=ujima" "nosuid" "nodev" "noexec"]
   "ext4"  ["rw" "nosuid" "nodev" "noexec"]
   "ntfs"  ["ro" "nosuid" "nodev" "noexec"]})

(def ^:private default-opts ["ro" "nosuid" "nodev" "noexec"])

(def ^:private fsck-of
  {"vfat"  mount/fsck-vfat!
   "exfat" mount/fsck-exfat!
   "ext4"  mount/fsck-ext4!})


(defn- ->mount-task [fstype uuid mnt conv]
  (flow :storage/mount

    (fs/create-dirs mnt)

    (mount/umount-lazy! mnt)

    (when-let [fsck! (fsck-of fstype)]
      (let [r (fsck! (str "/dev/disk/by-uuid/" uuid))]
        (when-not (:ok? r)
          (log/warn "storage: fsck did not come back clean" {:uuid uuid :fstype fstype}))))

    (mount/mount! fstype {:UUID uuid} mnt (mount-opts fstype default-opts))

    {:mount   mnt
     :storage (provision-root mnt (:storage conv))
     :tokens  (if (:tokens conv) (sweep mnt) {})}))


(defn- land!
  "T runs off the lock; the pass after it runs under it — act! releases the point if the
   stick left meanwhile."
  [t about]
  (task/run!
    (flow :storage/land
      (task/run!! t)
      (try (handle-event! {:type :storage/landed})
           (catch Throwable e
             (log/error "storage: the pass after a mount failed" (assoc about :error (ex-message e))))))))


(defn- start-mount! [uuid {:keys [fstype label]}]
  (let [t (->mount-task fstype uuid (mount-of uuid) (schema/convention label))]
    (swap! partitions* assoc-in [uuid :task] t)
    (land! t {:uuid uuid})))


(defn- ->peer-mount-task [{:keys [host access credentials]} mnt]
  (flow :storage/mount-peer
    (smb/mount! credentials host access mnt)
    {:mount mnt :storage mnt}))

(defn- start-peer-mount! [name {:keys [facts credentials]}]
  (let [t (->peer-mount-task (assoc facts :credentials credentials) (peer-mount-of name))]
    (swap! peers* assoc-in [name :task] t)
    (land! t {:peer name})))


(defn- with-partitions
  "KNOWN at linux's PARTITIONS: a new uuid has no task, a known one keeps its task, a departed
   one is gone."
  [known partitions]
  (into {} (for [[uuid facts] partitions]
             [uuid {:facts facts :task (get-in known [uuid :task])}])))

(defn- with-request
  "KNOWN with a peer root asked for: new, it is detected; failed, its task goes so it is tried
   again; in flight or mounted, left alone."
  [known {:keys [peer access] :as request}]
  (let [root (str peer "/" (name access))
        {:keys [task] :as entry} (known root)]
    (cond
      (nil? entry)                    (assoc known root {:facts       (select-keys request [:peer :access :host])
                                                         :credentials (:credentials request)})
      (= :invalid (task->state task)) (update known root dissoc :task)
      :else                           known)))

(defn- of-present? [peers [_root {:keys [facts]}]] (contains? peers (:peer facts)))

(defn- with-peers
  "KNOWN without the roots of any peer not among PEERS, the ids present."
  [known peers]
  (into {} (filter (partial of-present? peers) known)))


(defn- list-dir [dir] (when (fs/directory? dir) (fs/list-dir dir)))

(defn- known-partition? [partitions point] (contains? partitions (fs/file-name point)))
(defn- known-root?      [peers peer point] (contains? peers (str peer "/" (fs/file-name point))))
(defn- detected? [[_name {:keys [task]}]] (= :detected (task->state task)))


(defn- release-departed!
  "MOUNTS-DIR holds one point per known partition, PEERS-DIR one per peer and root; every
   other one is released — a device that left this run, or while ujimad was down."
  [mounts-dir peers-dir]
  (doseq [m (remove (partial known-partition? @partitions*) (list-dir mounts-dir))]
    (release-mount! (str m)))

  (doseq [peer-dir (list-dir peers-dir)]
    (let [peer (fs/file-name peer-dir)]
      (doseq [m (remove (partial known-root? @peers* peer) (list-dir peer-dir))]
        (release-mount! (str m)))
      (when (empty? (list-dir peer-dir))
        (fs/delete-if-exists peer-dir)))))


(defn- act!
  "EVENT folded into what is known, then the world made to match: departed mount points
   released, a mount task per detected partition and peer root. A mount lands on its own
   thread, which acts and converges again."
  [{:keys [type] :as event}]
  (let [partitions @partitions*
        peers      @peers*]

    (case type
      :storage/partitions (reset! partitions* (with-partitions partitions (:partitions event)))
      :circle/peers       (reset! peers*      (with-peers peers (:peers event)))
      :storage/mount      (reset! peers*      (with-request peers event))
      (:storage/init
       :storage/landed)   nil)

    (release-departed! (:mounts-dir @cfg*) (:peers-dir @cfg*))

    (doseq [[uuid {:keys [facts]}] (filter detected? @partitions*)]
      (start-mount! uuid facts))

    (doseq [[root known] (filter detected? @peers*)]
      (start-peer-mount! root known))))


(defn- converge! []
  (let [next (projection)
        prev @prev*]
    (reset! prev* next)
    (doseq [t (:targets @cfg*)] (t next prev))))


(defn handle-event!
  "One pass, whole, under the lock: `act!`'s check-then-act and `converge!`'s read-then-reset of
   prev are only guards while a pass cannot interleave. An event of a type not known here is
   refused. The events:
     {:type :storage/partitions :partitions {uuid facts}}      linux's removable partitions
     {:type :circle/peers :peers #{id}}                        the circle's present peers
     {:type :storage/mount :peer :access :host :credentials}   a peer root asked for (`mount!`)
     {:type :storage/landed}  {:type :storage/init}            a mount finished; the first pass"
  [event]
  (locking lock
    (act! event)
    (converge!)))


(defn mount!
  "A peer's Samba root, asked for with all a mount needs — nothing is looked up:
     (mount! {:peer \"87f6…\" :access :rw :host \"10.0.0.5\" :credentials \"/ujima/run/smb/credentials\"})
   Known and in flight or mounted, it is left alone; failed, it is tried again. The outcome
   is the entry [:peer \"87f6…/rw\"] in the snapshot."
  [request]
  (handle-event! (assoc request :type :storage/mount))
  nil)


;; --- wiring + read side -----------------------------------------------------

(defn- builtin-entry
  "A config-declared place, resolved by kind. :local is checked, never created — a
   dir over an unmounted mount is the RAM black hole. :session is created, never
   checked — its contract is ephemeral, so a RAM-backed empty dir is honest."
  [{:keys [kind mount provides] :as facts}]
  (let [base (-> (dissoc facts :provides)
                 (assoc :storage (provision-root mount (:storage provides))))]
    (case kind
      :local   (if (mount/mount-point? mount)
                 (assoc base :state :mounted :tokens {})
                 (-> (dissoc base :mount :storage)
                     (assoc :state :invalid :reason (str "not mounted: " mount))))
      :session (do (fs/create-dirs (:storage base))
                   (assoc base :state :mounted :tokens {})))))


(defn init! [{:keys [mounts-dir peers-dir places]}]
  (reset! partitions* {})
  (reset! peers*      {})
  (reset! prev*       nil)
  (reset! builtin-places* (mapv builtin-entry places))
  (reset! cfg*        {:mounts-dir (or mounts-dir default-mounts-dir)
                       :peers-dir  (or peers-dir default-peers-dir)
                       :targets    []})
  (handle-event! {:type :storage/init})
  nil)


(defn on-converge!
  "Attach a target: F gets (next, prev) after every pass, under the lock."
  [f]
  (swap! cfg* update :targets conj f)
  nil)


(defn snapshot [] (or @prev* []))
