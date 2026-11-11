(ns ujima.linux.net
  (:require [cheshire.core  :as json]
            [clojure.string :as str]
            [lib.util       :refer [distinct-by]]
            [lib.shell :refer [$?]]))


;; more interface names when x86 lands
(def ^:private interfaces ["eth0" "wlan0"])   ; allowlist AND rank: wired wins ties


(defn- parsed [{:keys [ok? out]}]
  (when ok? (json/parse-string out true)))


(defn- ipv4-of [addr]
  (let [inet (filter (fn [a] (= "inet" (:family a))) (:addr_info addr))]
    (or (first (filter (fn [a] (= "global" (:scope a))) inet))
        (first inet))))   ; link-scope 169.254: a zeroconf'd machine is still reachable


(defn- gateway-of [routes iface]
  (:gateway (first (filter (fn [r] (and (= "default" (:dst r)) (= iface (:dev r))))
                           routes))))


(defn- facts-of [routes addr]
  (let [v4 (ipv4-of addr)]
    {:up      (= "UP" (:operstate addr))
     :ip      (:local v4)
     :prefix  (:prefixlen v4)
     :mac     (:address addr)
     :gateway (gateway-of routes (:ifname addr))
     :dhcp    (boolean (:dynamic v4))}))   ; the address's own flag — works gatewayless


(defn interface-facts
  "{:eth0 {:up :ip :prefix :mac :gateway :dhcp} :wlan0 …}; absent = no such device."
  []
  (let [routes  (parsed ($? ip -j route))
        by-name (into {} (map (juxt :ifname identity)) (parsed ($? ip -j addr)))]
    (into {} (keep (fn [iface]
                     (when-let [addr (by-name iface)]
                       [(keyword iface) (facts-of routes addr)]))
                   interfaces))))


(defn lan-ip
  "The address peers reach us at: the routed interface's, else the first up one."
  [facts]
  (let [ranked (keep facts (map keyword interfaces))]
    (or (:ip (first (filter :gateway ranked)))
        (:ip (first (filter (fn [f] (and (:up f) (:ip f))) ranked))))))


(defn broadcast-of
  "The directed broadcast address of IP's /PREFIX network."
  [ip prefix]
  (let [n    (reduce (fn [acc octet] (+ (* acc 256) (parse-long octet))) 0 (str/split ip #"\."))
        hosts (dec (bit-shift-left 1 (- 32 prefix)))]
    (str/join "." (for [shift [24 16 8 0]] (bit-and (bit-shift-right (bit-or n hosts) shift) 255)))))


(defn networks
  "Each network we are up on: our address there, and the address a datagram reaches every
   machine there at. Two interfaces on one network count once."
  [facts]
  (->> (vals facts)
       (filter (fn [{:keys [up ip prefix]}] (and up ip prefix)))
       (map (fn [{:keys [ip prefix]}] {:host ip :broadcast (broadcast-of ip prefix)}))
       (distinct-by :broadcast)
       vec))
