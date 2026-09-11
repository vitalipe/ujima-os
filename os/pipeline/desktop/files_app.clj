(ns pipeline.desktop.files-app
  "The Files app's C++ host, compiled INSIDE the image chroot (aarch64 under qemu) from the
   mirrored /ujima/desktop/files tree, so an image build produces the binary with no device
   in the loop. Only the image build compiles: a live `dev script desktop` skips this (the
   toolchain is not image content, and a live device keeps whatever bin/ it has). The
   build-deps are installed for the build and purged after it, the app's declared runtime
   (os/apps/files/install.edn) pinned manual so autoremove keeps it."
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]
            [lib.shell :refer [sh! sh?]]))


(def ^:private src   "/ujima/desktop/files")
(def ^:private build "/tmp/ujima-files-build")
(def ^:private bin   "/ujima/desktop/files/bin/ujima-files")

(def ^:private build-deps
  ["qt6-base-dev" "qt6-declarative-dev" "qt6-svg-dev" "cmake" "ninja-build" "g++"])


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
  (cond
    (not (image-chroot?))
    (println "files-app: not the image chroot — keeping" (if (fs/exists? bin) "the bin/ already there" "no binary"))

    (not (fs/exists? (str src "/CMakeLists.txt")))
    (println "files-app: no source at" src "— nothing to build")

    :else
    (let [missing (remove installed? build-deps)
          t0      (System/nanoTime)]
      (when (seq missing)
        (when-not (seq (fs/glob "/var/lib/apt/lists" "*Packages"))
          (sh! :apt-get "update"))
        ;; the app's runtime rides along explicitly (= manual), so the purge below can't take it
        (apply sh! :apt-get "install" "-y" "--no-install-recommends" (concat (runtime-deps project) missing))
        (println "files-app: build-deps installed in" (secs t0)))

      (let [t1 (System/nanoTime)]
        (when (fs/exists? build) (fs/delete-tree build))
        (sh! :cmake "-S" src "-B" build "-G" "Ninja" "-DCMAKE_BUILD_TYPE=Release")
        (sh! :cmake "--build" build)
        (fs/create-dirs (fs/parent bin))
        (sh! :install "-m" "0755" (str build "/ujima-files") bin)
        (fs/delete-tree build)
        (println "files-app: built" bin "in" (secs t1)))

      (when (seq missing)
        (let [t2 (System/nanoTime)]
          ;; the dev headers and whatever only they pulled in go; the runtime above is manual
          (apply sh! :apt-get "purge" "-y" "--auto-remove" missing)
          (println "files-app: build-deps purged in" (secs t2)))))))
