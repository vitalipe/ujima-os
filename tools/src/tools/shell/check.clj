(ns tools.shell.check
  "The shell's state matrix, played back on a Pi: every state in tools/shell/states.edn is set
   through the sim, the bars (and the lock card, and a tooltip under a hover) are captured off the
   screen, and every :check's click is held to the verbs the sim logs. `compare` diffs two runs —
   eww against Qt while both exist, a run against an approved one after. Captures are BMPs: maim
   writes them and a browser shows them, so nothing here decodes an image format."
  (:require [clojure.edn       :as edn]
            [clojure.string    :as str]
            [babashka.fs       :as fs]
            [babashka.process  :as p]
            [lib.shell         :refer [sh?]]
            [tools.shell.sim   :as sim]))


(def ^:private remote-dir "/tmp/shellcheck")
(def ^:private eww-dir    "/tmp/ewwsim")


;; ── the screen ──────────────────────────────────────────────────────────────

(defn- screen []
  (let [[_ w h] (re-find #"current (\d+) x (\d+)" (str (:out (sim/on-pi! "DISPLAY=:0 xrandr | head -1"))))]
    [(parse-long w) (parse-long h)]))

(defn- target
  "Where a named thing sits on a WxH screen — the bars' own metrics, right-anchored for the tray."
  [[w h] t]
  (case t
    :close    [(- w 29) 24]
    :home     [44 (- h 34)]
    :files    [102 (- h 34)]
    :volume   [(- w 101) (- h 34)]
    :keyboard [(- w 143) (- h 34)]
    :kbd      [(- w 167) (- h 34)]))

(defn- slider
  "The revealed volume slider's x span and y."
  [[w h]]
  [(- w 289) (- w 159) (- h 34)])

(defn- regions [[w h] {:keys [hover apps]}]
  (cond-> {"top"  [0 0 w 48]
           "dock" [0 (- h 70) w 70]}
    (= hover :close)         (assoc "below-top"  [0 48 w 60])
    (and hover (not= hover :close)) (assoc "above-dock" [0 (- h 140) w 70])
    (= :locked (:mode apps)) (assoc "lock" [(- (quot w 2) 300) (- (quot h 2) 250) 600 500])))

(defn- pointer! [[x y]] (sim/on-pi! (str "DISPLAY=:0 xdotool mousemove " x " " y)))
(defn- away! [[w _]] (pointer! [(quot w 2) 300]))

(defn- capture! [state-name regions]
  (sim/on-pi! (str/join " ; " (for [[region [x y w h]] regions]
                                (format "DISPLAY=:0 maim -f bmp -g %dx%d+%d+%d %s/%s-%s.bmp"
                                        w h x y remote-dir state-name region)))))

(defn- pull! [out]
  (fs/create-dirs out)
  (let [{:keys [password ssh-opts host]} (sim/pi)]
    (p/shell {:out :string :in nil} "bash" "-c"
             (str "sshpass -p " password " ssh -q -o LogLevel=ERROR " (str/join " " ssh-opts) " " host
                  " 'tar c -C " remote-dir " .' | tar x -C " out))))


;; ── eww, as the reference renderer (its clicks would reach the real ujimad — visuals only) ───

(defn- eww! [& args]
  (sim/on-pi! (str "DISPLAY=:0 eww --config " eww-dir " --no-daemonize " (str/join " " args))))

(defn- eww-up!
  "v05's eww config, pointed at the sim, in a scratch daemon; the Qt shell stopped meanwhile."
  [url]
  (sim/on-pi! "systemctl --user stop ujima-shell.service")
  (sim/on-pi! (str "rm -rf " eww-dir " && mkdir -p " eww-dir))
  (doseq [f ["eww.yuck" "eww.scss"]]
    (let [src (-> (:out (sh? :git "show" (str "v05:desktop/shell/eww/" f)))
                  (str/replace "http://127.0.0.1:1336/ujima-desktop" url))]
      (sh? {:in src} :sshpass "-p" (:password (sim/pi)) "ssh" "-q" "-o" "LogLevel=ERROR"
           (concat (:ssh-opts (sim/pi)) [(:host (sim/pi)) (str "cat > " eww-dir "/" f)]))))
  ;; -f and every fd redirected: a backgrounded job would hold the ssh channel open for its lifetime
  (sim/on-pi! (str "setsid -f env DISPLAY=:0 PATH=/ujima/desktop/bin:$PATH eww --config "
                   eww-dir " daemon --no-daemonize > " eww-dir ".log 2>&1 < /dev/null"))
  (loop [n 40]
    (when (and (pos? n) (not (:ok? (sim/on-pi! (str "DISPLAY=:0 eww --config " eww-dir " ping")))))
      (Thread/sleep 250) (recur (dec n))))
  (eww! "open-many" "topbar" "dock"))

(defn- eww-show! [{:keys [mode]}]
  (if (= :multi mode) (eww! "open-many" "topbar" "dock") (eww! "close" "topbar" "dock"))
  (if (= :locked mode) (eww! "open" "lockscreen") (eww! "close" "lockscreen")))

(defn- eww-down! []
  (sim/on-pi! (str "DISPLAY=:0 eww --config " eww-dir " kill"))
  (sim/on-pi! "systemctl --user start ujima-shell.service"))


;; ── the clicks ──────────────────────────────────────────────────────────────

(defn- posts []
  (mapv (fn [{:keys [route body]}] [route (not-empty body)]) @sim/log*))

(defn- drag!
  "One xdotool chain, so the moves land 80 ms apart — what a hand does, and what the throttle is for."
  [scr [from to]]
  (let [[x0 x1 y] (slider scr)
        at (fn [f] (Math/round (double (+ x0 (* f (- x1 x0))))))]
    (pointer! (target scr :volume))
    (Thread/sleep 500)
    (sim/on-pi! (str "DISPLAY=:0 xdotool mousemove " (at from) " " y " mousedown 1 "
                     (str/join " " (for [i (range 1 13)]
                                     (str "sleep 0.08 mousemove " (at (+ from (* (- to from) (/ i 12)))) " " y)))
                     " sleep 0.08 mouseup 1"))))

(defn- drag-ok?
  "The slider's contract: the first value at once, at most one per 250 ms, the last on release —
   and an unmute after it only when the drag started muted."
  [log [from to] unmute?]
  (let [vols   (keep (fn [[r b]] (when (= r "POST /commands/audio/volume") (:value b))) log)
        mutes  (keep (fn [[r b]] (when (= r "POST /commands/audio/muted") b)) log)
        near?  (fn [v f] (< (Math/abs (- v (* 100 f))) 6))]
    (and (seq vols) (near? (first vols) from) (near? (last vols) to)
         (<= (count vols) 10)
         (= (if unmute? [{:value false}] []) (vec mutes)))))

(defn- run-check! [scr {:keys [name state click drag expect]}]
  (sim/apply-preset! state)
  (away! scr)
  (Thread/sleep 900)
  (reset! sim/log* [])
  (if drag (drag! scr drag) (sim/on-pi! (let [[x y] (target scr click)]
                                          (str "DISPLAY=:0 xdotool mousemove " x " " y " click 1"))))
  (Thread/sleep 900)
  (away! scr)
  (let [log (posts)
        ok? (case expect
              :volume-drag        (drag-ok? log drag false)
              :volume-drag-unmute (drag-ok? log drag true)
              (= expect log))]
    (println (format "  %-4s %-20s %s" (if ok? "ok" "FAIL") name (pr-str log)))
    ok?))


;; ── the run ─────────────────────────────────────────────────────────────────

(defn check!
  [{:keys [ip renderer out clicks-only] :or {renderer "qt" out "out/shell-check"} :as opts}]
  (let [{:keys [url stop]} (sim/start! opts)
        eww?    (= renderer "eww")
        dir     (str (fs/path out renderer))
        {:keys [states checks]} (sim/matrix)
        done    (atom false)
        restore (fn [] (when (compare-and-set! done false true)
                         (when eww? (eww-down!))
                         (stop)))]
    ;; ctrl-c too must leave the Pi's shell up and dialling ujimad
    (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable restore))
    (try
      (when eww? (eww-up! url))
      (let [scr (screen)]
        (when-not clicks-only
          (sim/on-pi! (str "rm -rf " remote-dir " && mkdir -p " remote-dir))
          (println (str "capturing " (count states) " states (" renderer ", " (str/join "x" scr) ")"))
          (doseq [{:keys [name hover]} states
                  :let [st (sim/preset name)]]
            (sim/apply-preset! name)
            (when eww? (eww-show! (:apps st)))
            (away! scr)
            (Thread/sleep 1200)
            (when hover (pointer! (target scr hover)) (Thread/sleep 1100))
            (capture! name (regions scr st)))
          (away! scr)
          (pull! dir)
          (println "captured into" dir))
        (when-not eww?
          (println "clicks:")
          (let [results (mapv #(run-check! scr %) checks)]
            (println (format "%d of %d clicks ok" (count (filter true? results)) (count results))))))
      (finally (restore)))))


;; ── compare two runs ────────────────────────────────────────────────────────

(defn- bmp
  "maim's 24-bit BMP: a header, then bottom-up rows padded to 4 bytes."
  [f]
  (let [b   (fs/read-all-bytes f)
        u32 (fn [o] (reduce (fn [acc i] (bit-or acc (bit-shift-left (bit-and (aget b (+ o i)) 0xff) (* 8 i))))
                            0 (range 4)))
        w   (u32 18) h (u32 22) off (u32 10)
        row (* 4 (quot (+ (* 3 w) 3) 4))]
    {:w w :h h :b b :at (fn [x y] (+ off (* row (- h 1 y)) (* 3 x)))}))

(defn- differs
  "Pixels in [x0,x1) whose channels differ by more than 24 between A and B, B shifted by DX."
  [{ab :b a-at :at h :h} {bb :b b-at :at w :w} x0 x1 dx]
  (let [x0 (max x0 (- dx)) x1 (min x1 (- w dx))]
    (loop [y 0 x x0 n 0]
      (cond
        (>= y h)  n
        (>= x x1) (recur (inc y) x0 n)
        :else (let [i (a-at x y) j (b-at (+ x dx) y)
                    d (fn [k] (Math/abs (- (bit-and (aget ab (+ i k)) 0xff) (bit-and (aget bb (+ j k)) 0xff))))]
                (recur y (inc x) (if (or (> (d 0) 24) (> (d 1) 24) (> (d 2) 24)) (inc n) n)))))))

(defn- diff-region
  "Differing pixels. The dock's clock is masked (time passes between runs) and its tray, whose
   width follows the clock's digits, is aligned first: the best of ±8px."
  [region a b]
  (if (not= "dock" region)
    (differs a b 0 (:w a) 0)
    (let [w (:w a) tray (- w 420) clock (- w 70)]
      (+ (differs a b 0 tray 0)
         (apply min (for [dx (range -8 9)] (differs a b tray clock dx)))))))

(defn compare!
  [{:keys [a b out] :or {a "out/shell-check/eww" b "out/shell-check/qt" out "out/shell-check"}}]
  (let [skip  (into #{} (for [{:keys [name skip]} (:states (sim/matrix)) region skip]
                          (str name "-" region ".bmp")))
        names (sort (remove skip (filter #(fs/exists? (fs/path b %))
                                         (map #(str (fs/file-name %)) (fs/glob a "*.bmp")))))
        rows  (for [n names
                    :let [region (second (re-find #"-(top|dock|lock|below-top|above-dock)\.bmp$" n))
                          ia (bmp (fs/path a n)) ib (bmp (fs/path b n))
                          px (diff-region region ia ib)]]
                {:name n :px px :pct (/ (* 100.0 px) (* (:w ia) (:h ia)))})
        html  (str "<!doctype html><meta charset=utf-8><title>Shell check</title>"
                   "<style>body{background:#14171b;color:#e7eaef;font:13px system-ui;margin:16px}"
                   "img{display:block;max-width:100%;image-rendering:pixelated}td{vertical-align:top;padding:4px 8px;border-bottom:1px solid #282e38}"
                   ".bad{color:#d68383}</style><table>"
                   (str/join (for [{:keys [name px pct]} rows]
                               (format "<tr><td class='%s'>%s<br>%d px (%.2f%%)</td><td><img src='%s'><img src='%s'></td></tr>"
                                       (if (> pct 0.5) "bad" "") name px pct
                                       (str (fs/relativize out (fs/path a name)))
                                       (str (fs/relativize out (fs/path b name))))))
                   "</table>")]
    (spit (str (fs/path out "compare.html")) html)
    (doseq [{:keys [name px pct]} rows]
      (println (format "  %-4s %-36s %6d px  %5.2f%%" (if (> pct 0.5) "DIFF" "ok") name px pct)))
    (println "report:" (str (fs/path out "compare.html")))))
