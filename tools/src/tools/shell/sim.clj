(ns tools.shell.sim
  "A fake desktop tier for the shell, run on the laptop: the shell on a dev Pi is pointed at it
   (its unit's env file) and shows whatever state is set here — by hand from the page at /sim,
   or played back by `check`. Routes, the settings plane and both projections are ujimad's own;
   only the app layer is fake, since it needs i3. Only the data is fake. Never installed on an
   image."
  (:require [clojure.edn       :as edn]
            [clojure.java.io   :as io]
            [clojure.string    :as str]
            [babashka.fs       :as fs]
            [babashka.process  :as p]
            [lib.http          :as lib-http]
            [lib.shell         :refer [sh?]]
            [ujima.api.routes  :as routes]
            [ujima.control     :as control]
            [ujima.desktop.app                :as app]
            [ujima.desktop.app.catalog        :as catalog]
            [ujima.desktop.app.catalog.loader :as loader]
            [ujima.desktop.app.projection     :as proj]
            [ujima.desktop.http               :as shell-http]
            [ujima.desktop.http.converge      :as converge]
            [tools.circle.state :as state]
            [tools.cmd.dev      :as dev]))


(def ^:private default-port 1346)
(def ^:private state-file   (state/file "ujima-shell-sim.edn"))
(def ^:private stop-timeout-ms 5000)
(def ^:private env-file     "$XDG_RUNTIME_DIR/ujima-shell.env")

(defonce facts* (atom nil))   ; {:apps {…} :settings {…}} — what the page edits
(defonce log*   (atom []))    ; what the shell sent, oldest first
(defonce ^:private pi*    (atom nil))   ; the ssh transport to the Pi the shell runs on


;; ── the matrix ──────────────────────────────────────────────────────────────

(defn- deep-merge [& ms]
  (apply merge-with (fn [a b] (if (and (map? a) (map? b)) (deep-merge a b) b)) ms))

(defn matrix []
  (edn/read-string (slurp (io/resource "tools/shell/states.edn"))))

(defn preset [name]
  (let [{:keys [base states]} (matrix)]
    (or (some->> states (filter #(= name (:name %))) first (deep-merge base))
        (throw (ex-info (str "no such state: " name) {:name name})))))


;; ── the Pi ──────────────────────────────────────────────────────────────────

(defn pi [] @pi*)

(defn on-pi!
  "CMD (one shell string) on the Pi the shell runs on; nil when none is attached."
  [cmd]
  (when-let [{:keys [password ssh-opts host]} @pi*]
    (apply sh? :sshpass "-p" password "ssh" "-q" "-o" "LogLevel=ERROR" (concat ssh-opts [host cmd]))))

(defn- lan-ip
  "This machine's address on the route to IP — what the Pi dials back."
  [ip]
  (or (second (re-find #"src (\S+)" (str (:out (sh? :ip "-4" :route :get ip)))))
      (throw (ex-info (str "no route to " ip) {:ip ip}))))

(defn- point-shell! [url]
  (on-pi! (str "printf 'UJIMA_DESKTOP=%s\\n' '" url "' > " env-file
               " && systemctl --user restart ujima-shell.service")))

(defn- release-shell! []
  (on-pi! (str "rm -f " env-file " && systemctl --user restart ujima-shell.service")))


;; ── settings: ujimad's own plane, in a temp dir, with only the UI port attached ───

(defn- apply-settings!
  "Replace the three scopes the bar reads, so a state is the same however it was reached."
  [{:keys [name output volume muted layouts layout locked]}]
  (let [vol (when output [:audio output :volume])]
    (control/update-settings! :device (constantly {[:system :name] name
                                                   [:keyboard :available-layouts] layouts}))
    (control/update-settings! :session (constantly (cond-> {[:audio :muted]    muted
                                                            [:keyboard :layout] layout}
                                                     output (assoc [:audio :active] output
                                                                   vol volume))))
    (control/update-settings! :activity (constantly (cond-> {}
                                                      (and vol (locked :volume)) (assoc vol volume)
                                                      (locked :muted)  (assoc [:audio :muted] muted)
                                                      (and output (locked :output)) (assoc [:audio :active] output)
                                                      (locked :layout) (assoc [:keyboard :layout] layout))))))


;; ── apps: a fake i3 world through ujimad's own projection ───────────────────

(defn- world [{:keys [open focused titles fullscreen mode]}]
  {:focused-ws (cond (= mode :locked) proj/lock-ws
                     focused          (name focused)
                     :else            proj/home-ws)
   :catalog    (catalog/current)
   :ws->wins   (into {} (for [id open]
                          [(name id) [{:title       (get titles id (get-in (catalog/current) [:by-id id :label]))
                                       :focused?    (= id focused)
                                       :fullscreen? (contains? fullscreen id)}]]))})

(defn- on-device
  "Where an icon lives on the Pi — eww reads the stream's paths straight off its disk; the Qt
   shell asks for assets/app-icon/<id>, which this tier serves from the repo."
  [entry]
  (update entry :icon #(some-> % (str/replace #"^os/apps/" "/ujima/apps/")
                                 (str/replace #"^desktop/" "/ujima/desktop/"))))

(defn- snapshot [{:keys [mode] :as apps}]
  (-> (app/snapshot mode (world apps))
      (update :running #(mapv on-device %))
      (update :catalog #(mapv on-device %))
      (update :current #(some-> % on-device))))

(defn- show-lock!
  "The lock card only shows on its workspace: switch the Pi there and back, as ujimad does."
  [was now]
  (cond (and (= now :locked) (not= was :locked)) (on-pi! (str "DISPLAY=:0 i3-msg workspace " proj/lock-ws))
        (and (= was :locked) (not= now :locked)) (on-pi! (str "DISPLAY=:0 i3-msg workspace " proj/home-ws))))

(defn- apply-apps! [{:keys [console mode] :as apps}]
  (catalog/merge-app! :console {:hidden (not console)})
  (converge/converge-apps! (snapshot apps) nil)
  (show-lock! (get-in @facts* [:apps :mode]) mode))


;; ── facts in, from the page, a preset or a fake verb ────────────────────────

(defn- normalize
  "The page speaks JSON: ids and enums arrive as strings, sets as arrays."
  [{:keys [apps settings]}]
  (let [kw  #(some-> % not-empty keyword)
        kws #(into [] (keep kw) %)]
    {:apps     (-> apps
                   (update :open #(kws %))
                   (update :focused kw)
                   (update :mode kw)
                   (update :fullscreen #(set (kws %)))
                   (update :titles #(into {} (map (fn [[k v]] [(keyword (name k)) v])) %)))
     :settings (-> settings
                   (update :output kw)
                   (update :locked #(set (kws %)))
                   (update :volume #(some-> % long)))}))

(defn apply-facts!
  "FACTS = {:apps :settings}, keywordized. Settings first: a bar that sees an app change
   reads the settings it already has."
  [{:keys [apps settings] :as facts}]
  (apply-settings! settings)
  (apply-apps! apps)
  (reset! facts* facts))

(defn apply-preset! [name] (apply-facts! (preset name)))


;; ── the app verbs, against the fake world (the settings verbs stay ujimad's) ───

(defn- swap-apps!
  "F over the apps facts, then republish — refused, like ujimad, outside multi."
  [f & args]
  (when (= :multi (get-in @facts* [:apps :mode]))
    (let [apps (apply f (:apps @facts*) args)]
      (apply-apps! apps)
      (swap! facts* assoc :apps apps))))

(defn- ring [{:keys [open]}]
  (filter (set open) (:order (catalog/current))))

(defn- cycle-focus [{:keys [focused] :as apps} step]
  (let [r (vec (ring apps))
        i (.indexOf r focused)]
    (if (empty? r)
      apps
      (assoc apps :focused (if (neg? i)
                             (if (pos? step) (first r) (peek r))
                             (r (mod (+ i step) (count r))))))))

(defn- fake-app-verbs []
  {"app/open"      (fn [{:keys [app]}]
                     (let [id (keyword app)]
                       (catalog/resolve! id)
                       (swap-apps! #(-> % (update :open (fn [o] (vec (distinct (conj o id)))))
                                          (assoc :focused id)))))
   "app/close"     (fn [_] (swap-apps! #(-> % (update :open (fn [o] (vec (remove #{(:focused %)} o))))
                                              (assoc :focused nil))))
   "app/home"      (fn [_] (swap-apps! assoc :focused nil))
   "app/next"      (fn [_] (swap-apps! cycle-focus 1))
   "app/prev"      (fn [_] (swap-apps! cycle-focus -1))
   "app/open-url"  (fn [_] nil)
   "app/open-file" (fn [_] nil)})

(defn- with-fakes
  "ujimad's verb table with the app verbs answered here. Every app/* verb is faked: an
   unfaked one would reach an app layer that is not running."
  [verbs fakes]
  (let [app-verbs (set (filter #(str/starts-with? % "app/") (keys verbs)))]
    (assert (= app-verbs (set (keys fakes)))
            (str "the fake is out of step with the desktop tier — app verbs it does not fake: "
                 (sort (remove fakes app-verbs)) ", fakes for verbs it does not have: "
                 (sort (remove app-verbs (keys fakes)))))
    (reduce-kv (fn [m k f] (assoc-in m [k :handler] f)) verbs fakes)))


;; ── the listener ────────────────────────────────────────────────────────────

(defn- logged
  "Every POST the shell makes lands in the log the page and `check` read."
  [route handler]
  (if (str/starts-with? route "POST")
    (fn [req]
      (let [resp (handler req)]
        (swap! log* conj {:at (System/currentTimeMillis) :route (str/replace route #"\s+" " ")
                          :body (:body req) :status (:status resp)})
        resp))
    handler))

(defn- desktop-tier
  "ujimad's own desktop endpoints, the command routes rebuilt over the faked verbs."
  [static-root]
  (let [real  (shell-http/endpoints {:static-root static-root})
        fake  (routes/commands {:base "commands" :commands (with-fakes shell-http/verbs (fake-app-verbs))})]
    (assert (every? (set (keys (:routes real))) (keys fake)) "a faked command route ujimad does not serve")
    (update real :routes #(into {} (map (fn [[k h]] [k (logged k h)])) (merge % fake)))))

(defn status []
  {:facts   @facts*
   :catalog (catalog/listing (catalog/current))
   :states  (mapv #(select-keys % [:name :doc]) (:states (matrix)))
   :log     (vec (take-last 60 @log*))})

(defn- page [_]
  {:status 200 :headers {"content-type" "text/html; charset=utf-8"}
   :body   (slurp (io/resource "tools/shell/sim.html"))})

(def ^:private sim-tier
  {:routes {"GET  /"       page
            "GET  /state"  (fn [_] {:status 200 :body (status)})
            "POST /facts"  (fn [{body :body}] (apply-facts! (normalize body)) {:status 200 :body (status)})
            "POST /preset" (fn [{{:keys [name]} :body}] (apply-preset! name) {:status 200 :body (status)})
            "POST /clear-log" (fn [_] (reset! log* []) {:status 200 :body (status)})}})


;; ── up / down ───────────────────────────────────────────────────────────────

(defn- static-root!
  "The tier's static tree: the repo's shell icons + wall.svg, and the Pi's own wall.png (the
   image build rasterizes it; the repo has none). Copies — the tier refuses symlinks out."
  [dir]
  (let [root (str (fs/create-dirs (fs/path dir "static")))]
    (fs/copy-tree "desktop/shell/icons" (str (fs/path root "icons")) {:replace-existing true})
    (fs/copy "desktop/shell/wall.svg" (str (fs/path root "wall.svg")) {:replace-existing true})
    (let [{:keys [password ssh-opts host]} @pi*
          png (str (fs/path root "wall.png"))]
      (apply p/shell {:out (io/file png)} "sshpass" "-p" password "ssh" "-q" "-o" "LogLevel=ERROR"
             (concat ssh-opts [host "cat /ujima/desktop/shell/wall.png"])))
    root))

(defn start!
  "Serve the tier from this machine and point IP's shell at it: {:url, :stop — undoes both}."
  [{:keys [ip http-port state] :or {http-port default-port state "home"} :as opts}]
  (let [dir (str (fs/create-temp-dir {:prefix "ujima-shell-sim"}))
        url (str "http://" (lan-ip ip) ":" http-port "/ujima-desktop")]
    (reset! pi* (dev/ssh-transport (merge {:user "ujima" :password "ujima" :port "22"} opts)))
    (reset! log* [])
    (control/init! {:storage (str (fs/path dir "settings")) :tmp (str (fs/path dir "session"))})
    (converge/init!)
    (control/on-converge! converge/converge-ui!)
    (catalog/init! (loader/load-catalog ["os/apps"] "desktop/shell/icons/launcher.svg"))
    (apply-preset! state)
    (let [stop-http (lib-http/listen! {:host "0.0.0.0" :port http-port :log (fn [& _])
                                       :endpoints {"ujima-desktop" (desktop-tier (static-root! dir))
                                                   "sim"           sim-tier}})]
      (point-shell! url)
      (println (str "shell sim up — the shell on " ip " dials " url))
      (println (str "  states: http://localhost:" http-port "/sim/"))
      {:url url :stop (fn [] (release-shell!) (stop-http))})))

(defn up!
  [{:keys [ip] :as opts}]
  (when-let [{:keys [pid] :as held} (state/read! state-file)]
    (when (state/ours? held)
      (throw (ex-info (str "a shell sim is already running (pid " pid ") — `bb shell sim down " ip "`")
                      {:pid pid}))))
  (let [{:keys [stop]} (start! opts)]
    (state/write! state-file (merge (state/me) {:ip ip}))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(do (stop) (state/clear! state-file))))
    (println "ctrl-c (or `bb shell sim down <ip>`) points the shell back at ujimad")
    @(promise)))

(defn down!
  "Stops a running sim (its hook points the shell back); with none running, points the
   Pi's shell back anyway."
  [{:keys [ip] :as opts}]
  (let [claim (state/read! state-file)]
    (if (and claim (state/ours? claim))
      (do (println "stopping pid" (:pid claim) "...")
          (state/stop! claim stop-timeout-ms))
      (do (reset! pi* (dev/ssh-transport (merge {:user "ujima" :password "ujima" :port "22"} opts)))
          (release-shell!)
          (state/clear! state-file)
          (println "no sim running — the shell on" ip "dials ujimad again")))))
