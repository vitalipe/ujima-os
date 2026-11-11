(ns ujima.linux.net-test
  "The summary pick over interface facts; the facts themselves are `ip -j`'s."
  (:require [clojure.test :refer [deftest is]]
            [ujima.linux.net :as net]))


(deftest lan-ip-picks-the-reachable-address
  (is (= "10.0.0.9"
         (net/lan-ip {:eth0  {:up true :ip "10.0.0.7"}
                      :wlan0 {:up true :ip "10.0.0.9" :gateway "10.0.0.1"}}))
      "a gateway marks the routed interface, rank aside")
  (is (= "10.0.0.9"
         (net/lan-ip {:eth0  {:up false :ip nil}
                      :wlan0 {:up true :ip "10.0.0.9"}}))
      "gatewayless: the first up interface with an address")
  (is (= "10.0.0.7"
         (net/lan-ip {:eth0  {:up true :ip "10.0.0.7"}
                      :wlan0 {:up true :ip "10.0.0.9"}}))
      "wired wins a gatewayless tie")
  (is (nil? (net/lan-ip {})) "no interfaces, no address"))


(deftest networks-are-one-per-network-we-are-up-on
  (is (= "10.0.0.255"     (net/broadcast-of "10.0.0.246" 24)))
  (is (= "192.168.255.255" (net/broadcast-of "192.168.4.7" 16)))
  (is (= "10.0.0.7"       (net/broadcast-of "10.0.0.7" 32)) "a /32 reaches itself alone")
  (is (= [{:host "10.0.0.7" :broadcast "10.0.0.255"}]
         (net/networks {:eth0  {:up true :ip "10.0.0.7" :prefix 24}
                        :wlan0 {:up true :ip "10.0.0.9" :prefix 24}}))
      "two interfaces on one network, one entry")
  (is (= [{:host "10.0.0.9" :broadcast "10.0.0.255"}]
         (net/networks {:eth0  {:up false :ip "10.1.0.7" :prefix 24}
                        :wlan0 {:up true :ip "10.0.0.9" :prefix 24}
                        :lo    {:up true :ip nil :prefix nil}}))
      "down, or without an address, sends nowhere"))
