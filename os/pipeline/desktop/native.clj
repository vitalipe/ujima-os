(ns pipeline.desktop.native
  "The desktop's native pieces, compiled INSIDE the image chroot (aarch64 under qemu) from the
   mirrored /ujima/desktop tree, so an image build produces them with no device in the loop:
   the Files app's C++ host and the GTK chooser module. Only the image build compiles: a live
   `dev script desktop` skips this (the toolchain is not image content; a live device keeps what
   it built by hand). Every target's build-deps are installed once before the builds and purged
   once after; the Files app's declared runtime (os/apps/files/install.edn) is pinned manual so
   the purge keeps it."
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]
            [lib.shell :refer [sh! sh?]]))


(def ^:private files-src   "/ujima/desktop/files")
(def ^:private files-build "/tmp/ujima-files-build")
(def ^:private files-bin   "/ujima/desktop/files/bin/ujima-files")
(def ^:private lib-dir     "/ujima/desktop/lib")


(defn- build-files-app! []
  (when (fs/exists? files-build) (fs/delete-tree files-build))
  (sh! :cmake "-S" files-src "-B" files-build "-G" "Ninja" "-DCMAKE_BUILD_TYPE=Release")
  (sh! :cmake "--build" files-build)
  (fs/create-dirs (fs/parent files-bin))
  (sh! :install "-m" "0755" (str files-build "/ujima-files") files-bin)
  (fs/delete-tree files-build))


(def ^:private targets
  [{:name       "files app"
    :src        files-src
    :marker     "CMakeLists.txt"
    :build-deps ["qt6-base-dev" "qt6-declarative-dev" "qt6-svg-dev" "cmake" "ninja-build" "g++"]
    :build!     build-files-app!}
   {:name       "chooser module"
    :src        "/ujima/desktop/chooser"
    :marker     "chooser.c"
    :build-deps ["libgtk-3-dev" "pkg-config" "gcc"]
    :build!     #(sh! :sh "/ujima/desktop/chooser/build.sh" lib-dir)}])


(defn- image-chroot?
  "qemu-static is injected into the chroot for a script's lifetime only — a device never has it."
  []
  (fs/exists? "/usr/bin/qemu-aarch64-static"))


(defn- installed? [pkg]
  (:ok? (sh? :dpkg "-s" pkg)))


(defn- runtime-deps [project]
  (:apt (edn/read-string (slurp (str project "/os/apps/files/install.edn")))))


(defn- secs [t0] (format "%.0fs" (/ (- (System/nanoTime) t0) 1e9)))


(defn build!
  [project]
  (if-not (image-chroot?)
    (println "native: not the image chroot — keeping whatever the device built")
    (let [present (filter #(fs/exists? (str (:src %) "/" (:marker %))) targets)
          missing (remove installed? (distinct (mapcat :build-deps present)))
          t0      (System/nanoTime)]
      (doseq [{:keys [name src]} (remove (set present) targets)]
        (println "native:" name "— no source at" src ", nothing to build"))
      (when (seq missing)
        (when-not (seq (fs/glob "/var/lib/apt/lists" "*Packages"))
          (sh! :apt-get "update"))
        ;; the Files app's runtime rides along explicitly (= manual), so the purge can't take it
        (apply sh! :apt-get "install" "-y" "--no-install-recommends" (concat (runtime-deps project) missing))
        (println "native: build-deps installed in" (secs t0)))

      (doseq [{:keys [name build!]} present]
        (let [t1 (System/nanoTime)]
          (build!)
          (println "native:" name "built in" (secs t1))))

      (when (seq missing)
        (let [t2 (System/nanoTime)]
          ;; the dev headers and whatever only they pulled in go; the runtimes above are manual
          (apply sh! :apt-get "purge" "-y" "--auto-remove" missing)
          (println "native: build-deps purged in" (secs t2)))))))
