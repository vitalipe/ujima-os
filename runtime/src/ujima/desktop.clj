(ns ujima.desktop
  (:require [lib.shell :as shell]
            [ujima.log :as log]
            [ujima.desktop.app                :as app]
            [ujima.desktop.app.catalog.loader :as loader]))


(def ^:private x-tries    60)   ; x 250ms = ~15s for X to accept an authorized connection


(defn- await-x!
  []
  (loop [n x-tries]
    (when-not (:ok? (shell/sh? :setxkbmap "-query"))
      (if (pos? n)
        (do (Thread/sleep 250) (recur (dec n)))
        (log/warn "X never accepted an authorized connection — proceeding" {})))))


(defn- portal-init!
  "The portal bridge (org.freedesktop.portal.Desktop -> the Qt picker). No :portal configured
   means no portal — a dev host without the session bus stays quiet."
  [bin]
  (when bin
    (future
      (loop []
        (let [{:keys [exit]} @(shell/with-spawn 
                                (shell/inheriting shell/*spawn*)
                                (shell/sh bin))]
        
          (log/warn "portal bridge exited — respawning" {:exit exit})
          (Thread/sleep 2000)
          (recur))))))


(defn init!
  "X first, then the app layer over the scanned catalog, then the portal. The shell
   (desktop/shell) is not started here: it is a user unit the session binds."
  [{app-cfg :app :keys [portal] :as cfg}]
  
  (log/info "opening the desktop" cfg)
  
  (await-x!)
  (app/init! (merge (select-keys app-cfg [:open-web-app-bin :serve-web-app-bin])
                    {:catalog (loader/load-catalog (:catalog app-cfg) (:fallback-icon app-cfg))}))
  (portal-init! portal))
