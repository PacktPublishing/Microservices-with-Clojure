(ns helping-hands.service.persistence
  "Persistence Port and Adapter for Service"
  (:require [datomic.api :as d]))

;; --------------------------------------------------
;; Service Persistence Port for Adapters to Plug-in
;; --------------------------------------------------
(defprotocol ServiceDB
  "Abstraction for service database"

  (upsert [this id t provider area cost rating status]
    "Adds/Updates a service entity")

  (entity [this id flds]
    "Gets the specified service with all or requested fields")

  (delete [this id]
    "Deletes the specified service entity"))

;; --------------------------------------------------
;; Datomic Adapter Implementation for Service Port
;; --------------------------------------------------

(defn- get-entity-id
  [conn id]
  (-> (d/q '[:find ?e
             :in $ ?id
             :where [?e :service/id ?id]] (d/db conn) (str id))
      ffirst))

(defn- get-entity
  [conn id]
  (let [eid (get-entity-id conn id)]
    (->> (d/entity (d/db conn) eid) seq (into {}))))

(defrecord ServiceDBDatomic [conn]
  ServiceDB

  (upsert [this id t provider area cost rating status]
    (d/transact conn
                (vector (into {} (filter (comp some? val)
                                         {:db/id id
                                          :service/id id
                                          :service/type t
                                          :service/provider provider
                                          :service/area area
                                          :service/cost cost
                                          :service/rating rating
                                          :service/status status})))))

  (entity [this id flds]
    (when-let [service (get-entity conn id)]
      (if (empty? flds)
        service
        (select-keys service (map keyword flds)))))

  (delete [this id]
    (when-let [eid (get-entity-id conn id)]
      (d/transact conn [[:db.fn/retractEntity eid]]))))

(defn create-service-database
  "Creates a service database and returns the connection"
  [d]
  ;; create and connect to the database
  (let [dburi (str "datomic:mem://" d)
        db (d/create-database dburi)
        conn (d/connect dburi)]
    ;; transact schema if database was created
    (when db
      (d/transact conn
                  [{:db/ident :service/id
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Unique Service ID"
                    :db/unique :db.unique/identity
                    :db/index true}
                   {:db/ident :service/type
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Type of Service"
                    :db/index true
                    :db/fulltext true}
                   {:db/ident :service/provider
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Associated Service Provider ID"
                    :db/index false}
                   {:db/ident :service/area
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/many
                    :db/doc "Service Areas / Locality"
                    :db/index true
                    :db/fulltext true}
                   {:db/ident :service/cost
                    :db/valueType :db.type/float
                    :db/cardinality :db.cardinality/one
                    :db/doc "Hourly Cost"
                    :db/index false}
                   {:db/ident :service/rating
                    :db/valueType :db.type/float
                    :db/cardinality :db.cardinality/many
                    :db/doc "List of ratings"
                    :db/index false}
                   {:db/ident :service/status
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc "Status of Service (A/NA/D)"
                    :db/index false}]))
    (ServiceDBDatomic. conn)))
