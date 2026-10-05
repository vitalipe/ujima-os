(ns ujima.desktop.http.ui
  "The /ui read that is not a stream: the switcher's next layout, one-shot."
  (:require [ujima.control          :as control]
            [ujima.control.queries  :as queries]))


(defn keyboard-next
  "The switcher's next layout, so the keybind needn't tap the state stream."
  []
  {:next (queries/next-keyboard-layout (control/settings))})

