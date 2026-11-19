(ns ujima.circle.peers
  "The peers as data: id -> {:host :port :boot :seq :hash :seen-ms}, all but seen-ms off the
   beat, and what was read off each. A map and a clock in, a map or a view out.")


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

(defn state-of
  "How present a peer last heard at SEEN-MS is at NOW."
  [seen-ms now]
  (let [age (- now seen-ms)]
    (cond (<= age unsure-ms)  :online
          (<= age offline-ms) :unsure
          :otherwise          :offline)))

(defn render
  "PEERS as served at NOW: [{:id \"87f6…\" :address {:host \"10.0.0.5\" :port 1337} :state :online} …], by id.
   With PROFILES (id -> {:name :shares}) a row also carries :name and :shares, nil and [] until read."
  ([peers now] (render peers nil now))
  ([peers profiles now]
   (->> peers
        (map (fn [[id {:keys [host port seen-ms]}]]
               (cond-> {:id id :address {:host host :port port} :state (state-of seen-ms now)}
                 profiles (merge {:name nil :shares []} (profiles id)))))
        (sort-by :id)
        (into []))))

(defn- id-tail [id] (subs id (max 0 (- (count id) 4))))

(defn nearby
  "Every present peer's shares, by host then label; two hosts of one name get the id's tail:
   [{:peer-id \"87f6…\" :address {:host \"10.0.0.5\" :port 1337} :host \"Mvule 87f6\" :label \"math\" :access :rw} …]"
  [rendered]
  (let [present (remove (comp #{:offline} :state) rendered)
        clashes (->> present (map :name) frequencies (filter (fn [[_ n]] (> n 1))) (map key) set)
        host    (fn [{:keys [id name]}] (if (clashes name) (str name " " (id-tail id)) name))]
    (->> (for [{:keys [id address shares] :as peer} present
               {:keys [name access]} shares]
           {:peer-id id :address address :host (host peer) :label name :access access})
         (sort-by (juxt :host :label))
         (into []))))
