(ns helping-hands.consumer.persistence
  "Persistence Port and Adapter for Consumer Service"
  (:require [datomic.api :as d]
            [helping-hands.consumer.config :as cfg]))

;; --------------------------------------------------
;; Consumer Persistence Port for Adapters to Plug-in
;; --------------------------------------------------
(defprotocol ConsumerDB
  "Abstraction for consumer database"

  (upsert [this id name address mobile email geo]
    "Adds/Updates a consumer entity")

  (entity [this id flds]
    "Gets the specified consumer with all or requested fields")

  (delete [this id]
    "Deletes the specified consumer entity")

  (close [this]
    "Closes the database"))

;; --------------------------------------------------
;; Datomic Adapter Implementation for Consumer Port
;; --------------------------------------------------

(defn- get-entity-id
  [conn id]
  (-> (d/q '[:find ?e
             :in $ ?id
             :where [?e :consumer/id ?id]] (d/db conn) (str id))
      ffirst))

(defn- get-entity
  [conn id]
  (let [eid (get-entity-id conn id)]
    (->> (d/entity (d/db conn) eid) seq (into {}))))

(defrecord ConsumerDBDatomic [conn]
  ConsumerDB

  (upsert [this id name address mobile email geo]
    (d/transact conn
                (vector (into {} (filter (comp some? val)
                                         {:db/id id
                                          :consumer/id id
                                          :consumer/name name
                                          :consumer/address address
                                          :consumer/mobile mobile
                                          :consumer/email email
                                          :consumer/geo geo})))))

  (entity [this id flds]
    (when-let [consumer (get-entity conn id)]
      (if (empty? flds)
        consumer
        (select-keys consumer (map keyword flds)))))

  (delete [this id]
    (when-let [eid (get-entity-id conn id)]
      (d/transact conn [[:db.fn/retractEntity eid]])))

  (close [this]
    (d/shutdown true)))

(defn create-consumer-database
  "Creates a consumer database and returns the connection"
  []
  ;; create and connect to the database
  (let [dburi (cfg/get-config [:datomic :uri])
        db (d/create-database dburi)
        conn (d/connect dburi)]
    ;; transact schema if database was created
    (when db
      (d/transact conn
                  [{:db/ident :consumer/id
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Unique Consumer ID"
                    :db/unique :db.unique/identity
                    :db/index true}
                   {:db/ident :consumer/name
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Display Name for the Consumer"
                    :db/index true
                    :db/fulltext true}
                   {:db/ident :consumer/address
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Consumer Address"
                    :db/index true
                    :db/fulltext true}
                   {:db/ident :consumer/mobile
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Consumer Mobile Number"
                    :db/index false}
                   {:db/ident :consumer/email
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Consumer Email Address"
                    :db/index true}
                   {:db/ident :consumer/geo
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Latitude,Longitude CSV"
                    :db/index false}]))
    (ConsumerDBDatomic. conn)))
