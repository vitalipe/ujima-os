(ns ujima.desktop
  (:require [lib.shell :as shell]
            [ujima.log :as log]
            [ujima.desktop.app                :as app]
            [ujima.desktop.guard              :as guard]
            [ujima.desktop.app.catalog.loader :as loader]
            [ujima.linux.memory               :as memory]
            [ujima.linux.systemd              :as systemd]))


(def ^:private x-tries    60)   ; x 250ms = ~15s for X to accept an authorized connection


(defn- await-x!
  []
  (loop [n x-tries]
    (when-not (:ok? (shell/sh? :setxkbmap "-query"))
      (if (pos? n)
        (do (Thread/sleep 250) (recur (dec n)))
        (log/warn "X never accepted an authorized connection — proceeding" {})))))


(defn init!
  "X first, then the app layer over the scanned catalog. The shell (desktop/shell) and the
   portal (desktop/bin/ujima-portal) are not started here: they are user units the session binds."
  [{app-cfg :app :as cfg}]
  
  (log/info "opening the desktop" cfg)
  
  (await-x!)
  
  (when-not (systemd/memory-accounting?)
    (log/error "memory cgroup controller is off — per-app memory caps are NOT enforced" {}))

  (when-not (memory/psi?)
    (log/error "PSI is off — the memory-pressure guard is blind" {}))

  (guard/init!)
  (app/init! (merge (select-keys app-cfg [:open-web-app-bin :serve-web-app-bin])
                    {:catalog (loader/load-catalog (:catalog app-cfg) (:fallback-icon app-cfg))})))
