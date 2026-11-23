(ns ujima.linux.smb-test
  "The password derivation and the argv shapes; mocked at the spawn, nothing is launched."
  (:require [clojure.test     :refer [deftest is]]
            [babashka.fs      :as fs]
            [babashka.process :as p]
            [clojure.string   :as str]
            [lib.shell        :as shell]
            [ujima.linux.smb  :as smb]))


(def ^:private token "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef")


(deftest password-is-derived-from-the-token
  (let [password (smb/password-for token)]
    (is (= "f651c28058f9686e38e91c630a4717e214e17100fd62f64068a0d17d11a93f65" password)
        "frozen: every machine of the circle must derive the same")
    (is (not= token password) "the token itself never reaches a passdb")
    (is (not= password (smb/password-for (str/replace token "dead" "beef"))))))


;; `$!` slurps a real process, `$?` derefs — is-active answers by ACTIVE?, the rest runs `true`
(defn- calls-of [active? f]
  (let [calls* (atom [])]
    (binding [shell/*spawn* (fn [_opts argv]
                              (swap! calls* conj argv)
                              (if (some #{"is-active"} argv)
                                (atom {:exit (if active? 0 3) :out "" :err ""})
                                (p/process ["true"] {:out :stream})))]
      (f))
    @calls*))


(deftest a-peer-root-is-mounted-with-the-spike-options
  (let [dir (fs/create-temp-dir {:prefix "ujima-smb-test"})
        mnt (str (fs/path dir "rw"))
        at  (fn [root] (first (calls-of false #(smb/mount! "/ujima/run/smb/credentials" "10.0.0.5" root mnt))))]
    (try
      (is (= ["sudo" "-n" "mount" "-t" "cifs" "-o"
              (str "credentials=/ujima/run/smb/credentials,vers=3.1.1,soft,echo_interval=2,"
                   "noserverino,uid=ujima,gid=ujima,nosuid,nodev,noexec")
              "//10.0.0.5/rw" mnt]
             (at :rw)))
      (is (str/ends-with? (nth (at :ro) 6) ",ro") "the ro root is mounted read-only as well")
      (is (= "//10.0.0.5/ro" (nth (at :ro) 7)))
      (is (fs/directory? mnt) "the mount point is made")
      (finally (fs/delete-tree dir)))))


(deftest the-credentials-file-is-written-once-per-password
  (let [dir (str (fs/create-temp-dir))]
    (with-redefs [smb/run-dir dir]
      (is (true? (smb/credentials-file! "pw1")))
      (is (= "username=ujima\npassword=pw1\n" (slurp (str dir "/credentials"))))
      (is (nil? (smb/credentials-file! "pw1")) "the same again is left alone")
      (is (true? (smb/credentials-file! "pw2")))
      (is (= "username=ujima\npassword=pw2\n" (slurp (str dir "/credentials")))))))
