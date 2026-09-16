(ns console.main
  "Circle, Setup and Update over the real circle, and the trial-boot page. The token arrives
   in the environment, never argv. UJIMA_SELF is the machine we administer — its subnet is the
   one swept. UJIMA_CONSOLE_PORT lets the tryboot app sit beside the Console."
  (:require [console.circle :as circle]
            [console.http  :as http]))


(def ^:private ui-root "ui")

(def ^:private ui-roots
  {:console    ui-root
   :circle     (str ui-root "/circle")
   :setup      (str ui-root "/setup")
   :update     (str ui-root "/update")
   :trial-boot (str ui-root "/trial-boot")})


(defn -main [& _]
  (let [key  (System/getenv "UJIMA_CIRCLE_TOKEN")
        port (or (some-> (System/getenv "UJIMA_CONSOLE_PORT") parse-long) 1338)]
    (when-not key
      (println "console: no UJIMA_CIRCLE_TOKEN — nothing can be signed, the circle stays empty"))
    (circle/init! {:key key :self-addr (or (System/getenv "UJIMA_SELF") "127.0.0.1")})
    (http/init! {:port port :ui-roots ui-roots})
    (println (str "console: http://127.0.0.1:" port "/   circle /circle/   setup /setup/"
                  "   update /update/   trial-boot /trial-boot/"))
    @(promise)))
