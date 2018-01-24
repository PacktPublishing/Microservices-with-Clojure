(ns helping-hands.order.persistence
  "Persistence Port and Adapter for Order"
  (:require [datomic.api :as d]))

;; --------------------------------------------------
;; Order Persistence Port for Adapters to Plug-in
;; --------------------------------------------------
(defprotocol OrderDB
  "Abstraction for order database"

  (upsert [this id service provider consumer
           cost start end rating status]
    "Adds/Updates an order entity")

  (entity [this id flds]
    "Gets the specified order with all or requested fields")

  (orders [this uid flds]
    "Gets all the orders of the authenticated user with all or requested fields")

  (delete [this id]
    "Deletes the specified order entity"))

;; --------------------------------------------------
;; Datomic Adapter Implementation for Order Port
;; --------------------------------------------------

(defn- get-entity-id
  [conn id]
  (-> (d/q '[:find ?e
             :in $ ?id
             :where [?e :order/id ?id]] (d/db conn) (str id))
      ffirst))

(defn- get-entity
  [conn id]
  (let [eid (get-entity-id conn id)]
    (->> (d/entity (d/db conn) eid) seq (into {}))))

(defn- get-entity-uid
  [conn uid]
  (->> (d/q '[:find ?e
              :in $ ?id
              :where [?e :order/consumer ?id]] (d/db conn) (str uid))
       (into []) flatten))

(defn- get-all-entities
  [conn uid]
  (let [eids (get-entity-uid conn uid)]
    (map #(->> (d/entity (d/db conn) %) seq (into {})) eids)))

(defrecord OrderDBDatomic [conn]
  OrderDB

  (upsert [this id service provider consumer
           cost start end rating status]
    (d/transact conn
                (vector (into {} (filter (comp some? val)
                                         {:db/id id
                                          :order/id id
                                          :order/service service
                                          :order/provider provider
                                          :order/consumer consumer
                                          :order/cost cost
                                          :order/start start
                                          :order/end end
                                          :order/rating rating
                                          :order/status status})))))

  (entity [this id flds]
    (when-let [order (get-entity conn id)]
      (if (empty? flds)
        order
        (select-keys order (map keyword flds)))))

  (orders [this uid flds]
    (when-let [orders (get-all-entities conn uid)]
      (if (empty? flds)
        orders
        (map #(select-keys % (map keyword flds)) orders))))

  (delete [this id]
    (when-let [eid (get-entity-id conn id)]
      (d/transact conn [[:db.fn/retractEntity eid]]))))

(defn create-order-database
  "Creates a order database and returns the connection"
  [d]
  ;; create and connect to the database
  (let [dburi (str "datomic:mem://" d)
        db (d/create-database dburi)
        conn (d/connect dburi)]
    ;; transact schema if database was created
    (when db
      (d/transact conn
                  [{:db/ident :order/id
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Unique Order ID"
                    :db/unique :db.unique/identity
                    :db/index true}
                   {:db/ident :order/service
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Associated Service ID"
                    :db/index false}
                   {:db/ident :order/provider
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Associated Service Provider ID"
                    :db/index false}
                   {:db/ident :order/consumer
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Associated Consumer ID"}
                   {:db/ident :order/cost
                    :db/valueType :db.type/float
                    :db/cardinality :db.cardinality/one
                    :db/doc "Hourly Cost"
                    :db/index false}
                   {:db/ident :order/start
                    :db/valueType :db.type/long
                    :db/cardinality :db.cardinality/one
                    :db/doc "Start Time (EPOCH)"
                    :db/index false}
                   {:db/ident :order/end
                    :db/valueType :db.type/long
                    :db/cardinality :db.cardinality/one
                    :db/doc "End Time (EPOCH)"
                    :db/index false}
                   {:db/ident :order/rating
                    :db/valueType :db.type/float
                    :db/cardinality :db.cardinality/many
                    :db/doc "List of ratings"
                    :db/index false}
                   {:db/ident :order/status
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Status of Order (O/I/D/C)"
                    :db/index false}]))
    (OrderDBDatomic. conn)))
