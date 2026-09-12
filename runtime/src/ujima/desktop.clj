(ns ujima.desktop
  (:require [lib.shell :as shell]
            [ujima.log :as log]
            [ujima.desktop.eww :as eww]))


(def ^:private x-tries    60)   ; x 250ms = ~15s for X to accept an authorized connection


(defn await-x!
  []
  (loop [n x-tries]
    (when-not (:ok? (shell/sh? :setxkbmap "-query"))
      (if (pos? n)
        (do (Thread/sleep 250) (recur (dec n)))
        (log/warn "X never accepted an authorized connection — proceeding" {})))))


(defn- launcher-init!
  "The home surface (desktop/launcher, a Qt Quick window): kept up for the whole session. It
   finds this daemon's desktop tier on its own — nothing to pass."
  [bin]
  (future
    (loop []
      (let [{:keys [exit]} @(shell/with-spawn (shell/inheriting shell/*spawn*)
                              (shell/sh bin))]
        (log/warn "launcher exited — respawning" {:exit exit})
        (Thread/sleep 2000)
        (recur)))))


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


(defn init!! [cfg]

  (log/info "opening ujima shell" cfg)

  (launcher-init! (:launcher cfg))


  (portal-init! (:portal cfg))
  (eww/init!! (:eww cfg))) 

