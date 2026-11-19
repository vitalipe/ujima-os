(ns ujima.circle.client
  "One circle member over HTTP: a signed request, its verified answer. Every request is
   signed, gated or not — one client rule, nothing to drift. An open route never
   checks the stamp, so a read stamps with our clock; a command reads the target's
   clock first and stamps with that, which is the clock its gate measures against.
   A PEER is a roster entry as ujima.circle hands it out, with the circle key on it."
  (:require [babashka.http-client :as http]
            [clojure.edn          :as edn]
            [lib.edn              :refer [edn->json]]
            [lib.hmac             :as hmac]
            [lib.http.signature   :as sig]))


(def ^:private default-port 1337)

;; a command must resolve inside the jobs deadline (5s)
(def timeouts {:clock 1500 :poll 1500 :command 8000})


(defn- one-shot
  "Never throws: a dead socket, or no key to sign with, is a verdict."
  [{:keys [key id address]} method uri body timeout-ms ts]
  (if-not key
    {:verdict :keyless}
    (let [nonce  (hmac/nonce)
          ts     (str (or ts (System/currentTimeMillis)))
          signed (sig/sign-request key {:method method :path uri :target id
                                        :ts     ts     :nonce nonce :body (or body "")})]
      (try
        (let [{:keys [status headers body]}
              (http/request (cond-> {:method  method
                                     :uri     (str "http://" (:host address) ":" (or (:port address) default-port)
                                                   uri "?format=edn")
                                     :headers {sig/request-header
                                               (sig/request-header-value {:ts ts :nonce nonce :sig signed})}
                                     :timeout timeout-ms
                                     :throw   false}
                              body (assoc :body body)))]
          (if (sig/verify-response key
                                   {:status status :path uri :nonce nonce :body body}
                                   (:sig (sig/parse-header (get headers sig/response-header))))
            {:verdict :answered
             :http    status
             :answer  (try (edn/read-string body) (catch Exception _ nil))}
            {:verdict :unverified}))
      (catch Exception _ {:verdict :noreply})))))


(defn- call
  "One round trip, stamped TS — ours when nil."
  [peer method uri body timeout-ms ts]
  (let [{:keys [verdict http answer]} (one-shot peer method uri body timeout-ms ts)]
    (case verdict
      :keyless    {:status :fail    :data {:reason :no-key}}
      :noreply    {:status :noreply :data {:reason :transport}}
      :unverified {:status :fail    :data {:reason :auth/bad-response}}
      :answered   (if (<= 200 http 299)
                    (cond-> {:status :ok} (seq answer) (assoc :data answer))
                    ;; the gate's :reason, a verb's :error, else the status
                    {:status :fail :data {:reason (or (:reason answer) (:error answer) http)}}))))


(defn machine
  "A peer's whole machine tree."
  [peer]
  (call peer :get "/api/query/machine" nil (:poll timeouts) nil))

(defn node
  "One node of a peer's machine tree: \"shares\" -> [{:name \"math\" :access :rw}],
   \"system/name\" -> {:name \"Mvule\"}."
  [peer path]
  (call peer :get (str "/api/query/machine/" path) nil (:poll timeouts) nil))

(defn peers
  "A peer's roster: who it hears."
  [peer]
  (call peer :get "/api/query/peers" nil (:poll timeouts) nil))

(defn command!
  "PATH is command-relative. A clear is its whole URL, so it carries no body."
  ([peer path] (command! peer path nil))
  ([peer path body]
   (let [{:keys [status data] :as clock}
         (call peer :get "/api/query/machine/system/clock-ms" nil (:clock timeouts) nil)]
     (if (= :ok status)
       (call peer :post (str "/api/commands/" path) (some-> body edn->json) (:command timeouts) (:clock-ms data))
       clock))))
