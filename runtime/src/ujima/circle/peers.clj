(ns ujima.circle.peers
  "The peers as data: id -> {:host :port :boot :seq :hash :seen-ms}, all but seen-ms off the
   beat. A map and a clock in, a map or a view out.")


(def ^:private unsure-ms  3000)   ;; one beat missed
(def ^:private offline-ms 7000)   ;; three missed


(defn admit
  "PEERS after hearing BEAT at NOW. A beat no later than the last from the same boot is
   one already heard — delayed, or replayed — and changes nothing."
  [peers {:keys [id host port boot seq hash]} now]
  (let [known (peers id)]
    (if (and known (= boot (:boot known)) (<= seq (:seq known)))
      peers
      (assoc peers id {:host host :port port :boot boot :seq seq :hash hash :seen-ms now}))))

(defn- state-of [seen-ms now]
  (let [age (- now seen-ms)]
    (cond (<= age unsure-ms)  :online
          (<= age offline-ms) :unsure
          :otherwise          :offline)))

(defn render
  "PEERS as served at NOW: [{:id \"87f6…\" :address {:host \"10.0.0.5\" :port 1337} :state :online} …], by id."
  [peers now]
  (->> peers
       (map (fn [[id {:keys [host port seen-ms]}]]
              {:id id :address {:host host :port port} :state (state-of seen-ms now)}))
       (sort-by :id)
       vec))
