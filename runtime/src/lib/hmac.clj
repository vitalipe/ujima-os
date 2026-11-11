(ns lib.hmac
  "A shared key signs a tagged tuple of fields. The tag is the first signed line, so a
   tuple signed under one tag never verifies under another; what the fields are is the
   caller's format. Pure — strings in, hex out."
  (:require [clojure.string :as str])
  (:import [java.security MessageDigest SecureRandom]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [java.util HexFormat]))


(def ^:private hex (HexFormat/of))


(defn sha256-hex [^String s]
  (.formatHex hex (.digest (MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))))


(defn- line
  ;; a newline inside a field would shift every boundary after it, so two
  ;; different tuples could canonicalise identically
  [v]
  (let [s (str v)]
    (when (str/includes? s "\n")
      (throw (ex-info "newline in a signed field" {:error :signature/bad-field :value s})))
    s))


(defn text
  "The canonical string signed for TAG and FIELDS."
  [tag fields]
  (str/join "\n" (cons tag (map line fields))))


(defn- hmac
  ;; the key's characters ARE the key — no hex decode, so nothing can fail to parse
  [^String key ^String signed-text]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. (.getBytes key "UTF-8") "HmacSHA256"))
    (.formatHex hex (.doFinal mac (.getBytes signed-text "UTF-8")))))


(defn- same?
  ;; constant time: `=` on the hex would leak the matching prefix a byte at a time
  [^String a b]
  (boolean
    (when (and a b)
      (MessageDigest/isEqual (.getBytes a "UTF-8") (.getBytes (str b) "UTF-8")))))


(defn sign   [key tag fields]     (hmac key (text tag fields)))
(defn verify [key tag fields sig] (boolean (when key (same? (hmac key (text tag fields)) sig))))


(defonce ^:private rng (SecureRandom.))


(defn nonce []
  (let [b (byte-array 16)]
    (.nextBytes rng b)
    (.formatHex hex b)))
