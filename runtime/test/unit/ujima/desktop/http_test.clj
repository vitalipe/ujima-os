(ns ujima.desktop.http-test
  "The shell module: its file serving (the wall comes only from the shell tree,
   containment checked after the path resolves) and its verbs."
  (:require [clojure.test :refer [deftest is]]
            [babashka.fs  :as fs]
            [lib.edn      :refer [edn->json]]
            [lib.http     :as http]
            [ujima.control.commands :as effects]
            [ujima.desktop.app      :as app]
            [ujima.desktop.http     :as shell]))


(defn- tree []
  (let [root (str (fs/create-temp-dir))]
    (spit (str root "/wall.png") "PNGDATA")
    root))


(defn- GET [root uri]
  ((http/app {:endpoints {"ujima-desktop" (shell/endpoints {:static-root root})} :log (fn [& _])})
   {:request-method :get :uri uri}))

(defn- POST
  ([uri] (POST uri nil))
  ([uri body]
   ((http/app {:endpoints {"ujima-desktop" (shell/endpoints {:static-root "/nowhere"})} :log (fn [& _])})
    (cond-> {:request-method :post :uri uri}
      body (assoc :body (edn->json body))))))


(deftest the-wall-is-served
  (is (= 200 (:status (GET (tree) "/ujima-desktop/assets/wall.png")))))


(deftest a-wall-that-leads-out-of-the-tree-is-not
  (let [root (str (fs/create-temp-dir))]
    (spit (str root "/../ESCAPED") "not under the root")
    (fs/create-sym-link (str root "/wall.png") (str root "/../ESCAPED"))
    (is (= 404 (:status (GET root "/ujima-desktop/assets/wall.png")))
        "containment is checked after the path resolves")))


(deftest the-command-verbs-reach-the-same-effects-api-does
  (let [seen (atom [])]
    (with-redefs [app/run!           (fn [id]  (swap! seen conj [:open id]))
                  app/close-focused! (fn []    (swap! seen conj [:close]))
                  app/go-home!       (fn []    (swap! seen conj [:home]))
                  app/open-url!      (fn [url] (swap! seen conj [:url url]))
                  app/cycle!         (fn [n]   (swap! seen conj [:cycle n]))]
      (is (every? #(= 202 (:status %))
                  [(POST "/ujima-desktop/commands/app/open" {:app "files"})
                   (POST "/ujima-desktop/commands/app/close")
                   (POST "/ujima-desktop/commands/app/home")
                   (POST "/ujima-desktop/commands/app/open-url" {:url "https://ujima.lan"})
                   (POST "/ujima-desktop/commands/app/next")
                   (POST "/ujima-desktop/commands/app/prev")]))
      (is (= [[:open :files] [:close] [:home] [:url "https://ujima.lan"] [:cycle 1] [:cycle -1]]
             @seen)))))


(deftest a-command-never-names-a-scope
  (let [seen (atom nil)]
    (with-redefs [effects/change-keyboard-layout! (fn [code scope] (reset! seen [code scope]))]
      (is (= 202 (:status (POST "/ujima-desktop/commands/keyboard/layout" {:layout "il"}))))
      (is (= ["il" :session] @seen) "the tier IS the session; the caller has no scope to get wrong"))
    (with-redefs [effects/change-setting! (fn [path value scope] (reset! seen [path value scope]))]
      (is (= 202 (:status (POST "/ujima-desktop/commands/audio/muted" {:value true}))))
      (is (= [[:audio :muted] true :session] @seen)
          "a named verb, not the generic settings write"))
    (with-redefs [effects/change-current-volume! (fn [value scope] (reset! seen [value scope]))]
      (is (= 202 (:status (POST "/ujima-desktop/commands/audio/volume" {:value 40}))))
      (is (= [40 :session] @seen) "the bar's slider, at the session's scope"))))


(deftest a-command-still-checks-its-params
  (is (= 400 (:status (POST "/ujima-desktop/commands/app/open" {}))))
  (is (= 400 (:status (POST "/ujima-desktop/commands/app/open-url" {:url ""}))))
  (is (= 400 (:status (POST "/ujima-desktop/commands/audio/muted" {:value "maybe"}))))
  (is (= 400 (:status (POST "/ujima-desktop/commands/audio/volume" {:value "loud"})))))


(deftest a-verbs-own-errors-are-named-here-too
  (let [boom (fn [kw] (fn [& _] (throw (ex-info "nope" {:error kw}))))]
    (with-redefs [effects/change-keyboard-layout! (boom :keyboard/unknown-layout)]
      (is (= 409 (:status (POST "/ujima-desktop/commands/keyboard/layout" {:layout "de"})))
          "the same status /api gives — an unnamed error would be a silent 500"))
    (with-redefs [effects/change-current-volume! (boom :audio/no-output)]
      (is (= 409 (:status (POST "/ujima-desktop/commands/audio/volume" {:value 40})))))
    (with-redefs [app/run! (boom :app/unknown-app)]
      (is (= 404 (:status (POST "/ujima-desktop/commands/app/open" {:app "nope"})))))
    (with-redefs [app/open-url! (boom :app/bad-url)]
      (is (= 400 (:status (POST "/ujima-desktop/commands/app/open-url" {:url "ftp://x"})))))))


(deftest the-desktop-listener-has-no-api-tier
  (is (= 404 (:status (POST "/api/commands/app/open" {:app "files"})))
      "/api is another machine's door — it is not on this socket at all"))
