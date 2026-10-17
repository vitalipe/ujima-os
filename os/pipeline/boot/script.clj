(ns pipeline.boot.script
  "Runs INSIDE the target chroot as root. Owns the boot partition: the overlayroot cmdline, the
   prebuilt initramfs that implements it, and the boot splash on top.

   The initramfs can't be generated here — update-initramfs segfaults under qemu — so the image
   ships a kernel-matched one from os/boot/initramfs, produced on a Pi by the dev kit's build-initramfs (pulled home by `bb pin initramfs <ip>`).

   Pipeline: install -> boot -> base -> runtime -> desktop -> ujimaify -> [dev] -> [cleanup].

   `project` is the read-only repo bind inside the chroot (default /ujima-src)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [lib.shell :refer [$! with-console-out]]
            [build.files :as files]
            [ujima.device.ab.autoboot.bootfiles :as bootfiles]))


(def ^:private boot-firmware   "/boot/firmware")
(def ^:private initramfs-src   "boot/initramfs")   ;; the concern dir under os/pipeline/
(def ^:private initramfs-files ["initramfs8" "initramfs_2712"])
(def ^:private frames-src      "desktop/splash/frames")
(def ^:private theme-dir       "/usr/share/plymouth/themes/ujima")


;; recurse=0 so the fstab submounts punch through instead of being overlaid too. No `rw` — the
;; tmpfs upper is the write layer. No fsck.repair — the ro lower can't corrupt. `root` is the one
;; token we don't pick: it stays whatever the image points at, and the installer re-points it.
;; The splash owns the screen until the session (Esc still shows the log); ignore-serial-consoles keeps
;; the serial console from pushing Plymouth into text mode. The firmware prepends `cgroup_disable=memory`;
;; cgroup_enable + cgroup_memory turn the controller back on (per-app caps), psi=1 the pressure accounting.
(defn- ujima-cmdline [root]
  [["overlayroot" "tmpfs:recurse=0"]
   ["console"     "serial0,115200"]
   ["console"     "tty1"]
   ["root"        root]
   ["rootfstype"  "ext4"]
   ["rootwait"    nil]
   ["quiet"       nil]
   ["splash"      nil]
   ["plymouth.ignore-serial-consoles" nil]
   ["logo.nologo" nil]
   ["vt.global_cursor_default" "0"]
   ["cgroup_enable" "memory"]
   ["cgroup_memory" "1"]
   ["psi"           "1"]])


(defn- write-cmdline! []
  (let [root (or (bootfiles/cmdline-get (bootfiles/cmdline boot-firmware) "root")
                 (throw (ex-info "cmdline.txt has no root= — refusing to guess the rootfs"
                                 {:path (str boot-firmware "/cmdline.txt")})))]
    (println "cmdline root=" root)
    (bootfiles/cmdline! boot-firmware (ujima-cmdline root))))


(defn- kernel-version
  "Base version shared by the installed rpi kernels, e.g. \"6.12.75+rpt\"."
  []
  (let [bases (->> (fs/list-dir "/lib/modules")
                   (map (comp str fs/file-name))
                   (filter #(str/includes? % "+rpt"))
                   (map #(str/replace % #"-rpi-.*$" ""))
                   distinct)]
    (when-not (= 1 (count bases))
      (throw (ex-info "could not derive a single rpi kernel base version from /lib/modules"
                      {:found bases})))
    (first bases)))


;; keyed by kernel version and hard-failing on a miss, so a base bump that outdates the stash stops
;; the build loudly instead of shipping a silently overlay-less image
(defn- bake-initramfs! [project]
  (let [version (kernel-version)
        src     (str project "/os/pipeline/" initramfs-src "/" version)]

    (doseq [f initramfs-files]
      (when-not (fs/exists? (str src "/" f))
        (throw (ex-info (str "no prebuilt initramfs for kernel " version
                             " — regenerate with the build-initramfs producer (dev kit) on a Pi "
                             "and copy into os/pipeline/" initramfs-src "/" version)
                        {:version version :missing (str src "/" f)}))))

    (doseq [f initramfs-files]
      ($! cp (str src "/" f) (str boot-firmware "/" f)))

    (println "baked prebuilt initramfs" version)))


(defn- frame-table
  "The script's frame table, from the manifest `bb pin splash` writes beside the frames."
  [{:keys [fps heights intro loop]}]
  (str/join "\n" (concat [(str "frame_rate = " fps ";")]
                         (map-indexed #(str "frame_heights[" %1 "] = " %2 ";") heights)
                         [(str "frame_height_count = " (count heights) ";")
                          (str "intro_count = " intro ";")
                          (str "loop_count = " loop ";")
                          "" ""])))


;; Plymouth plays the intro, the loop and the rest pose; the outro is the shell's cover
(defn- install-theme! [project]
  (let [src      (str project "/" frames-src)
        manifest (json/parse-string (slurp (str src "/frames.json")) true)]
    ($! rm -rf [theme-dir])
    (doseq [h (:heights manifest)
            :let [dst (str theme-dir "/" h)]]
      (fs/create-dirs dst)
      (doseq [f (concat (fs/glob (str src "/" h) "intro-*.png")
                        (fs/glob (str src "/" h) "loop-*.png")
                        [(fs/path src (str h) "rest.png")])]
        (fs/copy f dst)))
    (files/install! project "boot/splash/ujima.plymouth" (str theme-dir "/ujima.plymouth"))
    (spit (str theme-dir "/ujima.script")
          (str (frame-table manifest) (slurp (files/source project "boot/splash/ujima.script"))))
    (files/install! project "boot/splash/plymouthd.conf" "/etc/plymouth/plymouthd.conf")
    (println "installed the splash theme:" (:heights manifest) "lines")))


;; The pin carries Plymouth, its plugin and vc4; the theme changes more often, so it rides as a second
;; archive the kernel unpacks on top. After switch-root plymouthd reads the rootfs copy.
(defn- append-theme! []
  (let [archive "/tmp/ujima-splash.cpio"
        theme   (->> (file-seq (io/file theme-dir))
                     (map #(subs (str %) 1))
                     sort)
        paths   (concat ["etc" "etc/plymouth" "etc/plymouth/plymouthd.conf"
                         "usr" "usr/share" "usr/share/plymouth" "usr/share/plymouth/themes"]
                        theme)]
    (p/shell {:dir "/" :in (str/join "\n" paths) :out (io/file archive)}
             "cpio" "-o" "-H" "newc" "--quiet")
    ;; the kernel takes an uncompressed archive only at an aligned offset, skipping zeros up to it
    (doseq [f initramfs-files
            :let [path (str boot-firmware "/" f)]]
      (with-open [out (io/output-stream path :append true)]
        (.write out (byte-array (mod (- (fs/size path)) 512)))
        (io/copy (io/file archive) out)))
    (println "appended the splash theme to the initramfs:" (quot (fs/size archive) 1024) "KB")
    (fs/delete archive)))


(defn run! [{:keys [project]}]
  (with-console-out
    (bake-initramfs! project)
    (install-theme! project)
    (append-theme!)
    (write-cmdline!)))
