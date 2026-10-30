(ns tools.splash.render
  "bb pin splash — the boot animation's frames from the vendored design, rendered by a Chrome on the
   host over CDP: the engine's export seek per frame, a crop around the stage centre, one set per
   screen height."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [babashka.http-client.websocket :as ws]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.server :as server]))


(def ^:private design-dir "tools/src/tools/splash/design")
(def ^:private page       "UjimaOS Boot Animation.dc.html")
(def ^:private out-dir    "desktop/splash/frames")

;; any Chromium will do: UJIMA_CHROME overrides, then a binary on PATH, then a flatpak
(def ^:private chrome-bins ["google-chrome" "google-chrome-stable" "chromium" "chromium-browser"])
(def ^:private chrome-apps ["com.google.Chrome" "org.chromium.Chromium"])

(def fps 25)
(def heights [720 1080 1440 2160])

;; the stage is 1920x1080 with the mark centred; this crop holds every scene's glow and lift, and
;; divides evenly at every height above (each is a multiple of 360 lines)
(def crop {:w 450 :h 480})


;; ── serving the design ──────────────────────────────────────────────────────

(def ^:private mime {"html" "text/html" "js" "text/javascript" "jsx" "text/plain"})

(defn- serve! []
  (server/run-server
    (fn [{:keys [uri]}]
      (let [f (fs/file design-dir (java.net.URLDecoder/decode (subs uri 1) "UTF-8"))]
        (if (fs/regular-file? f)
          {:status 200 :headers {"Content-Type" (mime (fs/extension f) "application/octet-stream")} :body f}
          {:status 404 :body "not found"})))
    {:port 0 :ip "127.0.0.1"}))


;; ── chrome over CDP ─────────────────────────────────────────────────────────

(defn- await! [what f]
  (loop [i 0]
    (or (f)
        (if (< i 200)
          (do (Thread/sleep 100) (recur (inc i)))
          (throw (ex-info (str "timed out waiting for " what) {}))))))

(defn- free-port []
  (with-open [s (java.net.ServerSocket. 0)] (.getLocalPort s)))

(defn- chrome-command
  "The argv prefix that starts a Chrome on this host."
  []
  (or (some-> (System/getenv "UJIMA_CHROME") not-empty (str/split #"\\s+"))
      (some #(when (fs/which %) [%]) chrome-bins)
      (when (fs/which "flatpak")
        (some #(when (zero? (:exit (p/sh "flatpak" "info" %))) ["flatpak" "run" %]) chrome-apps))
      (throw (ex-info "no Chrome on this host: set UJIMA_CHROME, or install one of these"
                      {:bins chrome-bins :flatpaks chrome-apps}))))

(defn- profile-dir [port] (str "/tmp/ujima-splash-chrome-" port))

(defn- launch-chrome!
  "A headless Chrome on a fresh port. A sandboxed browser (flatpak, snap) outlives its launcher, so
   it is closed over CDP (Browser.close), never by killing the launcher. The profile path is one the
   sandboxes resolve inside their own /tmp; a native Chrome gets it on the host."
  []
  (let [port (free-port)
        proc (p/process (into (chrome-command)
                              ["--headless=new" (str "--remote-debugging-port=" port)
                               (str "--user-data-dir=" (profile-dir port)) "--no-first-run"
                               "--no-default-browser-check" "--hide-scrollbars" "--mute-audio" "about:blank"]))]
    (await! "chrome's debug port"
            #(try (http/get (str "http://127.0.0.1:" port "/json/version")) (catch Exception _ nil)))
    {:proc proc :port port}))

(defn- close-chrome!
  "Browser.close on the browser's own endpoint, then wait for its port to go quiet."
  [{:keys [port]}]
  (try
    (let [url  (:webSocketDebuggerUrl
                 (json/parse-string (:body (http/get (str "http://127.0.0.1:" port "/json/version"))) true))
          sock (ws/websocket {:uri url})]
      (ws/send! sock (json/generate-string {:id 1 :method "Browser.close"}))
      (await! "chrome to exit"
              #(not (try (http/get (str "http://127.0.0.1:" port "/json/version")) (catch Exception _ nil)))))
    (catch Exception e
      (binding [*out* *err*] (println "warning: chrome did not close cleanly:" (ex-message e))))))

(defn- connect! [{:keys [port]}]
  (let [target  (json/parse-string
                  (:body (http/put (str "http://127.0.0.1:" port "/json/new?about:blank"))) true)
        pending (atom {})
        buf     (StringBuilder.)
        sock    (ws/websocket
                  {:uri (:webSocketDebuggerUrl target)
                   :on-message (fn [_ data last?]
                                 (.append buf data)
                                 (when last?
                                   (let [m (json/parse-string (str buf) true)]
                                     (.setLength buf 0)
                                     (some-> (get @pending (:id m)) (deliver m)))))})
        ids     (atom 0)]
    {:sock sock
     :call (fn [method params]
             (let [id (swap! ids inc)
                   pr (promise)]
               (swap! pending assoc id pr)
               (ws/send! sock (json/generate-string {:id id :method method :params params}))
               (let [r (deref pr 60000 ::timeout)]
                 (swap! pending dissoc id)
                 (cond (= r ::timeout) (throw (ex-info (str "CDP timeout: " method) {}))
                       (:error r)      (throw (ex-info (str "CDP error: " method) (:error r)))
                       :else           (:result r)))))}))

(defn- js [{:keys [call]} expr]
  (let [r (call "Runtime.evaluate" {:expression expr :awaitPromise true :returnByValue true})]
    (when-let [e (:exceptionDetails r)]
      (throw (ex-info "page script failed" e)))
    (-> r :result :value)))

(defn- metrics! [{:keys [call]} height dsf]
  (call "Emulation.setDeviceMetricsOverride" {:width 1920 :height height :deviceScaleFactor dsf :mobile false}))


;; ── the page ────────────────────────────────────────────────────────────────

(def ^:private stage-sel "[data-om-exportable-video-with-duration-secs]")

(defn- stage [cdp]
  (json/parse-string
    (js cdp (str "(() => { const s = document.querySelector('" stage-sel "');"
                 " if (!s) return null; const r = s.getBoundingClientRect();"
                 " return JSON.stringify({x: r.x, y: r.y, w: r.width, h: r.height}); })()"))
    true))

(defn- open!
  "Loads the design and returns the viewport height that renders its stage 1:1."
  [cdp port]
  (metrics! cdp 1080 1)
  ((:call cdp) "Page.navigate" {:url (str "http://127.0.0.1:" port "/" (.replace page " " "%20"))})
  (await! "the design's stage" #(stage cdp))
  ;; the preview's play bar takes the bottom of the viewport and shrinks the stage to fit: grow the
  ;; viewport by exactly the bar so the stage renders 1:1
  (let [height (+ 1080 (Math/round (- 1080.0 (:h (stage cdp)))))]
    (metrics! cdp height 1)
    (await! "a 1:1 stage" #(let [s (stage cdp)] (< (Math/abs (- (:h s) 1080.0)) 0.01)))
    height))

(defn- scenes [cdp]
  (let [named (json/parse-string (js cdp "window.OM_SCENES") true)]
    (loop [[s & more] named t 0.0 acc {}]
      (if s
        (recur more (+ t (:dur s)) (assoc acc (:name s) {:start t :dur (:dur s)}))
        acc))))

(defn- seek! [cdp t]
  (js cdp (str "(async () => { document.querySelector('" stage-sel "').dispatchEvent("
               "new CustomEvent('data-om-seek-to-time-frame', {detail: {time: " t ", sync: true, playing: false}}));"
               " await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))); return true; })()")))

(defn- shot! [{:keys [call] :as cdp} file]
  (let [{:keys [x y]} (stage cdp)
        clip {:x (+ x (/ (- 1920 (:w crop)) 2.0)) :y (+ y (/ (- 1080 (:h crop)) 2.0))
              :width (:w crop) :height (:h crop) :scale 1}
        png  (:data (call "Page.captureScreenshot" {:format "png" :clip clip :fromSurface true}))]
    (fs/write-bytes file (.decode (java.util.Base64/getDecoder) ^String png))))


;; ── frames ──────────────────────────────────────────────────────────────────

(defn- frame-times
  "[name t] per frame: the intro (Ignite..Settle), one loop period, the outro, and the rest pose."
  [cues]
  (let [loop-at  (:start (cues "Loop"))
        outro-at (:start (cues "Outro"))
        rest-at  (:start (cues "Rest"))
        span     (fn [prefix from to]
                   (for [i (range (Math/round (* (double (- to from)) fps)))]
                     [(format "%s-%04d.png" prefix i) (+ from (/ i (double fps)))]))]
    (when-not (and loop-at outro-at rest-at)
      (throw (ex-info "the design needs Loop, Outro and Rest scenes" {:scenes (keys cues)})))
    (concat (span "intro" 0.0 loop-at)
            (span "loop" loop-at outro-at)
            (span "outro" outro-at rest-at)
            [["rest.png" (+ rest-at (/ (:dur (cues "Rest")) 2))]])))

(defn render! []
  (let [stop   (serve!)
        port   (:local-port (meta stop))
        chrome (launch-chrome!)
        cdp    (connect! chrome)]
    (try
      (let [viewport (open! cdp port)
            frames   (frame-times (scenes cdp))]
        (fs/delete-tree out-dir)
        (doseq [h heights]
          (let [dir (fs/file out-dir (str h))]
            (fs/create-dirs dir)
            (metrics! cdp viewport (/ h 1080.0))
            (doseq [[name t] frames]
              (seek! cdp t)
              (shot! cdp (fs/file dir name)))
            (println (format "%5d lines: %d frames, %d KB" h (count frames)
                             (quot (reduce + (map fs/size (fs/list-dir dir))) 1024)))))
        ;; what the players need to know without listing files: the boot stage's Plymouth script
        ;; and the shell's cover both read this
        (let [counts (frequencies (keep #(second (re-find #"^(intro|loop|outro)-" (first %))) frames))]
          (spit (str out-dir "/frames.json")
                (json/generate-string {:fps fps :heights heights
                                       :intro (counts "intro") :loop (counts "loop") :outro (counts "outro")}))))
      (println "frames ->" out-dir)
      (finally
        (close-chrome! chrome)
        (p/destroy-tree (:proc chrome))
        (fs/delete-tree (profile-dir (:port chrome)))
        (stop)))))
