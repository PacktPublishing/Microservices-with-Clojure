(ns helping-hands.provider.core
  "Initializes Helping Hands Provider Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.provider.persistence :as p]
            [io.pedestal.interceptor.chain :as chain])
  (:import [java.io IOException]
           [java.util UUID]))

;; delay the check for database and connection
;; till the first request to access @providerdb
(def ^:private providerdb
  (delay (p/create-provider-database "provider")))

;; --------------------------------
;; Validation Interceptors
;; --------------------------------

(defn- validate-rating-ts
  "Validates the rating and timestamp"
  [context]
  (let [rating (-> context :request :form-params :rating)
        since_ts (-> context :request :form-params :since)]
    (try
      (let [context (if (not (nil? rating))
                      (assoc-in context [:request :form-params :rating]
                                (Float/parseFloat rating)) context)
            context (if (not (nil? since_ts))
                      (assoc-in context [:request :form-params :since]
                                (Long/parseLong since_ts)) context)]
        context)
      (catch Exception e nil))))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))
        ctx (validate-rating-ts context)
        params (if (not (nil? ctx))
                 (assoc params
                        :rating (-> ctx :request :form-params :rating)
                        :since (-> ctx :request :form-params :since)))]
    (if (and (not (empty? params))
             (not (nil? ctx))
             ;; any one of id or mobile
             (or (params :id) (params :mobile)))
      (let [flds (if-let [fl (:flds params)]
                   (map s/trim (s/split fl #","))
                   (vector))
            params (assoc params :flds flds)]
        (assoc context :tx-data params))
      (chain/terminate
       (assoc context
              :response {:status 400
                         :body (str "ID, mobile is mandatory "
                                    "and rating, since must be a number")})))))

(def validate-id
  {:name ::validate-id

   :enter
   (fn [context]
     (if-let [id (or (-> context :request :form-params :id)
                     (-> context :request :query-params :id)
                     (-> context :request :path-params :id))]
       ;; validate and return a context with tx-data
       ;; or terminated interceptor chain
       (prepare-valid-context context)
       (chain/terminate
        (assoc context
               :response {:status 400
                          :body "Invalid Provider ID"}))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def validate
  {:name ::validate

   :enter
   (fn [context]
     (if-let [params (-> context :request :form-params)]
       ;; validate and return a context with tx-data
       ;; or terminated interceptor chain
       (prepare-valid-context context)
       (chain/terminate
        (assoc context
               :response {:status 400
                          :body "Invalid parameters"}))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

;; --------------------------------
;; Business Logic Interceptors
;; --------------------------------

(def get-provider
  {:name ::provider-get

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           entity (.entity @providerdb (:id tx-data) (:flds tx-data))]
       (if (empty? entity)
         (assoc context :response {:status 404 :body "No such provider"})
         (assoc context :response {:status 200
                                   :body (jp/generate-string entity)}))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def upsert-provider
  {:name ::provider-upsert

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           id (:id tx-data)
           db (.upsert @providerdb id (:name tx-data)
                       (:mobile tx-data) (:since tx-data)
                       (:rating tx-data))]
       (if (nil? @db)
         (throw (IOException.
                 (str "Upsert failed for provider: " id)))
         (assoc context
                :response {:status 200
                           :body (jp/generate-string
                                  (.entity @providerdb id []))}))))
   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def create-provider
  {:name ::provider-create

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           ;; generate a random ID if it is not specified
           id (str (UUID/randomUUID))
           tx-data (if (:id tx-data) tx-data (assoc tx-data :id id))
           ;; create provider
           db (.upsert @providerdb id (:name tx-data)
                       (:mobile tx-data) (:since tx-data)
                       (:rating tx-data))]
       (if (nil? @db)
         (throw (IOException.
                 (str "Upsert failed for provider: " id)))
         (assoc context
                :response {:status 200
                           :body (jp/generate-string
                                  (.entity @providerdb id []))}))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})
(def delete-provider
  {:name ::provider-delete

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           db (.delete @providerdb (:id tx-data))]
       (if (nil? db)
         (assoc context :response {:status 404 :body "No such provider"})
         (assoc context :response {:status 200 :body "Success"}))))
   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})
