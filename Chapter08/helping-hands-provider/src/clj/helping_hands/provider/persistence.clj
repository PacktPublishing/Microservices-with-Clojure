(ns helping-hands.provider.persistence
  "Persistence Port and Adapter for Provider Service"
  (:require [datomic.api :as d]))

;; --------------------------------------------------
;; Provider Persistence Port for Adapters to Plug-in
;; --------------------------------------------------
(defprotocol ProviderDB
  "Abstraction for provider database"

  (upsert [this id name mobile since rating]
    "Adds/Updates a provider entity")

  (entity [this id flds]
    "Gets the specified provider with all or requested fields")

  (delete [this id]
    "Deletes the specified provider entity"))

;; --------------------------------------------------
;; Datomic Adapter Implementation for Provider Port
;; --------------------------------------------------

(defn- get-entity-id
  [conn id]
  (-> (d/q '[:find ?e
             :in $ ?id
             :where [?e :provider/id ?id]] (d/db conn) (str id))
      ffirst))

(defn- get-entity
  [conn id]
  (let [eid (get-entity-id conn id)]
    (->> (d/entity (d/db conn) eid) seq (into {}))))

(defrecord ProviderDBDatomic [conn]
  ProviderDB

  (upsert [this id name mobile since rating]
    (d/transact conn
                (vector (into {} (filter (comp some? val)
                                         {:db/id id
                                          :provider/id id
                                          :provider/name name
                                          :provider/mobile mobile
                                          :provider/since since
                                          :provider/rating rating})))))

  (entity [this id flds]
    (when-let [provider (get-entity conn id)]
      (if (empty? flds)
        provider
        (select-keys provider (map keyword flds)))))

  (delete [this id]
    (when-let [eid (get-entity-id conn id)]
      (d/transact conn [[:db.fn/retractEntity eid]]))))

(defn create-provider-database
  "Creates a provider database and returns the connection"
  [d]
  ;; create and connect to the database
  (let [dburi (str "datomic:mem://" d)
        db (d/create-database dburi)
        conn (d/connect dburi)]
    ;; transact schema if database was created
    (when db
      (d/transact conn
                  [{:db/ident :provider/id
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Unique Provider ID"
                    :db/unique :db.unique/identity
                    :db/index true}
                   {:db/ident :provider/name
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Display Name for the Provider"
                    :db/index true
                    :db/fulltext true}
                   {:db/ident :provider/mobile
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Provider Mobile Number"
                    :db/index false}
                   {:db/ident :provider/since
                    :db/valueType :db.type/long
                    :db/cardinality :db.cardinality/one
                    :db/doc "Provider Active Since EPOCH time"
                    :db/index false}
                   {:db/ident :provider/rating
                    :db/valueType :db.type/float
                    :db/cardinality :db.cardinality/many
                    :db/doc "List of ratings"
                    :db/index false}]))
    (ProviderDBDatomic. conn)))
