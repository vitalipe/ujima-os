(ns ujima.linux.smb
  "Samba as the circle uses it: one account, `ujima`, whose password every machine derives
   from the circle token; smbd up only on request; a peer's share root mounted over cifs.
   The config is the image's (os/pipeline/base/smb/smb.conf): two shares, `rw` and `ro`,
   each a directory of symlinks ujimad keeps."
  (:require [babashka.fs            :as fs]
            [lib.hmac               :as hmac]
            [lib.io                 :as io]
            [lib.shell              :refer [$?]]
            [ujima.linux.disk.mount :as mount]
            [ujima.linux.sudo       :refer [sudo! sudo$!]]))


(def ^:private account "ujima")
(def ^:private tag     "smb-v1")
(def ^:private run-dir "/ujima/run/smb")
(def ^:private unit    "smbd")

(def roots
  "The two shares smb.conf exports, by access, and the directory each serves."
  {:rw "/ujima/run/shares/rw"
   :ro "/ujima/run/shares/ro"})

;; the spike's: a frozen peer is noticed in 3 x echo_interval; the server requires encryption
(def ^:private mount-opts
  ["vers=3.1.1" "soft" "echo_interval=2" "noserverino" "uid=ujima" "gid=ujima" "nosuid" "nodev" "noexec"])


;; ── the account ─────────────────────────────────────────────────────────────

(defn password-for
  "The account's password under TOKEN: the same on every machine of the circle, never the
   token itself."
  [token]
  (hmac/sign token tag [account]))


(defn- run-file [name]
  (fs/create-dirs run-dir {:posix-file-permissions "rwx------"})
  (str run-dir "/" name))


(defn set-password!
  "PASSWORD into smbd's passdb, unless it is there already: the passdb and the marker both
   live on tmpfs, so a boot starts with neither. True when it ran."
  [password]
  (let [marker (run-file "password")
        digest (hmac/sha256-hex password)]
    (when (not= digest (io/slurp-text marker nil))
      (sudo! {:in (str password "\n" password "\n")} :smbpasswd :-s :-a account)
      (io/spit-file-atomic! marker digest)
      true)))


(defn credentials-file!
  "The file mount.cifs reads the account from; its path."
  [password]
  (let [path (run-file "credentials")]
    (io/spit-file-atomic! path (str "username=" account "\npassword=" password "\n"))
    path))


;; ── serving ─────────────────────────────────────────────────────────────────

(defn smbd? []
  (:ok? ($? systemctl is-active --quiet [unit])))


(defn smbd!
  "smbd up or down as ON? says, left alone when it already is. The state after."
  [on?]
  (when (not= on? (smbd?))
    (sudo$! systemctl [(if on? "start" "stop")] [unit]))
  (smbd?))


;; ── mounting a peer ─────────────────────────────────────────────────────────

(defn mount!
  "HOST's ROOT (:rw or :ro) onto MNT, read-only for the ro root. Throws when the host does
   not answer — a dead address in about 5 s."
  [credentials host root mnt]
  (fs/create-dirs mnt)
  (mount/mount! "cifs" (str "//" host "/" (name root)) mnt
                (cond-> (into [(str "credentials=" credentials)] mount-opts)
                  (= :ro root) (conj "ro"))))
