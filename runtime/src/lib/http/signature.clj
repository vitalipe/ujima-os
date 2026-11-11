(ns lib.http.signature
  "The signed-request wire format: the two canonical shapes and the two headers, on
   lib.hmac. Pure — strings and maps, no HTTP client or server in it.

   Both ends run THIS namespace. Everything either end could implement twice
   lives here, because a disagreement about the format is a silent auth failure
   that reads like a wrong key. Policy — the freshness window, the nonce cache,
   which routes are gated — is NOT here: each side owns its own."
  (:require [clojure.string :as str]
            [lib.hmac       :as hmac]))


(def request-header  "authorization")
(def response-header "response-signature")

;; one scheme token both directions: one thing to bump, one thing to compare.
;; It also names the scheme on `authorization`, which is shared with every other
;; auth scheme, so it stays distinctive rather than becoming a bare "v1".
(def scheme "HMAC1")

;; ── the two shapes ──────────────────────────────────────────────────────────

(def ^:private request-tag  "req-v1")
(def ^:private response-tag "res-v1")


(defn- request-fields
  "TARGET is who the request is FOR: one key is shared by every party, so without
   it a captured signature is valid against all of them."
  [{:keys [method path target ts nonce body]}]
  [(str/upper-case (name method)) path target ts nonce (hmac/sha256-hex (str body))])


(defn- response-fields
  "NONCE is the caller's, echoed — an absent one signs as empty, which is what an
   unsigned caller gets. STATUS is covered so a rejection cannot be flipped into
   an apparent success."
  [{:keys [status path nonce body]}]
  [status path nonce (hmac/sha256-hex (str body))])


;; Signing and building are ONE step on purpose: nothing can sign a request under
;; the response tag, which would look valid here and fail as a bad signature there.

(defn sign-request    [key parts]     (hmac/sign   key request-tag  (request-fields parts)))
(defn sign-response   [key parts]     (hmac/sign   key response-tag (response-fields parts)))

(defn verify-request  [key parts sig] (hmac/verify key request-tag  (request-fields parts)  sig))
(defn verify-response [key parts sig] (hmac/verify key response-tag (response-fields parts) sig))


;; ── headers ─────────────────────────────────────────────────────────────────

(defn- header-value [params]
  (str scheme " " (str/join " " (for [[k v] params] (str (name k) "=" v)))))


(defn request-header-value [{:keys [ts nonce sig]}]
  (header-value [[:ts ts] [:nonce nonce] [:sig sig]]))


(defn response-header-value [{:keys [nonce sig]}]
  (header-value [[:nonce nonce] [:sig sig]]))


(defn parse-header
  "\"<scheme> k=v k=v\" -> {:k \"v\"}; nil when the scheme isn't ours. Values stay
   STRINGS — ts is re-signed as it arrived, so coercing here would make \"007\"
   and \"7\" disagree with the signature."
  [header]
  (when-some [h (some-> header str/trim not-empty)]
    (let [[tag & pairs] (str/split h #"\s+")]
      (when (= scheme tag)
        (into {} (for [pair pairs
                       :let [[k v] (str/split pair #"=" 2)]
                       :when (seq k)]
                   [(keyword k) (or v "")]))))))
