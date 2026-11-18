(ns ujima.circle.beat
  "One heartbeat on the wire: {:id :host :port :boot :seq :hash}, signed with the circle key
   under this format's tag. Strings in, strings out."
  (:require [clojure.edn :as edn]
            [lib.hmac    :as hmac]))


(def ^:private tag "ujimaos-peer-beat-v1")
(def packet-max 512)


(defn digest
  "What a machine publishes -> the 8 hex its beat carries:
   {:name \"Mvule\" :shares [{:name \"math\" :access :rw}]} -> \"0112381b\""
  [published]
  (subs (hmac/sha256-hex (pr-str published)) 0 8))

(defn- fields [{:keys [id host port boot seq hash]}] [id host port boot seq hash])

(defn encode
  "The datagram for BEAT, signed with KEY."
  [key beat]
  (pr-str (assoc beat :sig (hmac/sign key tag (fields beat)))))

(defn decode
  "The beat in TEXT when it is well-formed and signed with KEY; nil for anything else."
  [key text]
  (let [{:keys [id host port boot seq hash sig] :as beat} (try (edn/read-string text) (catch Exception _ nil))]
    (when (and (string? id) (string? host) (int? port) (string? boot) (int? seq) (string? hash)
               (try (hmac/verify key tag (fields beat) sig) (catch Exception _ false)))
      (dissoc beat :sig))))
