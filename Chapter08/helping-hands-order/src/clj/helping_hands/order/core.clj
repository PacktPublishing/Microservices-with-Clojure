(ns helping-hands.order.core
  "Initializes Helping Hands Order Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.order.persistence :as p]
            [io.pedestal.interceptor.chain :as chain])
  (:import [java.io IOException]
           [java.util UUID]))

;; delay the check for database and connection
;; till the first request to access @orderdb
(def ^:private orderdb
  (delay (p/create-order-database "order")))

;; --------------------------------
;; Validation Interceptors
;; --------------------------------

(defn- service-exists?
  "Validates the service via Service APIs"
  [provider]
  ;;TODO Add integration with Service via clj-http
  true)

(defn- provider-exists?
  "Validates the provider via Provider service"
  [provider]
  ;;TODO Add integration with Provider service via clj-http
  true)

(defn- consumer-exists?
  "Validates the consumer via Consumer service"
  [provider]
  ;;TODO Add integration with Consumer service via clj-http
  true)

(defn- validate-rating-cost-ts
  "Validates the rating, cost and start/end timestamp"
  [context]
  (let [rating (-> context :request :form-params :rating)
        cost (-> context :request :form-params :cost)
        start (-> context :request :form-params :start)
        end (-> context :request :form-params :end)]
    (try
      (let [context (if (not (nil? rating))
                      (assoc-in context [:request :form-params :rating]
                                (Float/parseFloat rating)) context)
            context (if (not (nil? cost))
                      (assoc-in context [:request :form-params :cost]
                                (Float/parseFloat cost)) context)
            context (if (not (nil? start))
                      (assoc-in context [:request :form-params :start]
                                (Long/parseLong start)) context)
            context (if (not (nil? end))
                      (assoc-in context [:request :form-params :end]
                                (Long/parseLong end)) context)]
        context)
      (catch Exception e nil))))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))
        ctx (validate-rating-cost-ts context)
        params (if (not (nil? ctx))
                 (assoc params
                        :rating (-> ctx :request :form-params :rating)
                        :cost (-> ctx :request :form-params :cost)
                        :start (-> ctx :request :form-params :start)
                        :end (-> ctx :request :form-params :end)))]
    (if (and (not (empty? params))
             (not (nil? ctx))
             (params :id) (params :service) (params :provider)
             (params :consumer) (params :cost) (params :status)
             (contains? #{"O" "I" "D" "C"} (params :status))
             (service-exists? (params :service))
             (provider-exists? (params :provider))
             (consumer-exists? (params :consumer)))
      (let [flds (if-let [fl (:flds params)]
                   (map s/trim (s/split fl #","))
                   (vector))
            params (assoc params :flds flds)]
        (assoc context :tx-data params))
      (chain/terminate
       (assoc context
              :response {:status 400
                         :body (str "ID, service, provider, consumer, "
                                    "cost and status is mandatory. start/end, "
                                    "rating and cost must be a number with status "
                                    "having one of values O, I, D or C")})))))

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
                          :body "Invalid Order ID"}))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def validate-id-get
  {:name ::validate-id-get

   :enter
   (fn [context]
     (if-let [id (or (-> context :request :form-params :id)
                     (-> context :request :query-params :id)
                     (-> context :request :path-params :id))]
       ;; validate and return a context with tx-data
       ;; or terminated interceptor chain
       (let [params (merge (-> context :request :form-params)
                           (-> context :request :query-params)
                           (-> context :request :path-params))]
         (if (and (not (empty? params))
                  (params :id))
           (let [flds (if-let [fl (:flds params)]
                        (map s/trim (s/split fl #","))
                        (vector))
                 params (assoc params :flds flds)]
             (assoc context :tx-data params))
           (chain/terminate
            (assoc context
                   :response {:status 400
                              :body "Invalid Order ID"}))))
       (chain/terminate
        (assoc context
               :response {:status 400
                          :body "Invalid Order ID"}))))

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

(def validate-all-orders
  {:name ::validate-all-orders

   :enter
   (fn [context]
     (if-let [params (-> context :tx-data)]
       ;;Get user ID from auth uid
       (assoc-in context [:tx-data :flds]
                 (if-let [fl (-> context :request :query-params :flds)]
                   (map s/trim (s/split fl #","))
                   (vector)))
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

(def get-order
  {:name ::order-get

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           entity (.entity @orderdb (:id tx-data) (:flds tx-data))]
       (if (empty? entity)
         (assoc context :response {:status 404 :body "No such order"})
         (assoc context :response {:status 200
                                   :body (jp/generate-string entity)}))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def get-all-orders
  {:name ::order-get-all

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           entity (.orders @orderdb (:uid tx-data) (:flds tx-data))]
       (if (empty? entity)
         (assoc context :response {:status 404 :body "No such orders"})
         (assoc context :response {:status 200
                                   :body (jp/generate-string entity)}))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def upsert-order
  {:name ::order-upsert

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           id (:id tx-data)
           db (.upsert @orderdb (:id tx-data) (:service tx-data)
                       (:provider tx-data) (:consumer tx-data)
                       (:cost tx-data) (:start tx-data) (:end tx-data)
                       (:rating tx-data) (:status tx-data))]
       (if (nil? @db)
         (throw (IOException.
                 (str "Upsert failed for order: " id)))
         (assoc context
                :response {:status 200
                           :body (jp/generate-string
                                  (.entity @orderdb id []))}))))
   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def create-order
  {:name ::order-create

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           ;; generate a random ID if it is not specified
           id (str (UUID/randomUUID))
           tx-data (if (:id tx-data) tx-data (assoc tx-data :id id))
           ;; create order
           db (.upsert @orderdb (:id tx-data) (:service tx-data)
                       (:provider tx-data) (:consumer tx-data)
                       (:cost tx-data) (:start tx-data) (:end tx-data)
                       (:rating tx-data) (:status tx-data))]
       (if (nil? @db)
         (throw (IOException.
                 (str "Upsert failed for order: " id)))
         (assoc context
                :response {:status 200
                           :body (jp/generate-string
                                  (.entity @orderdb id []))}))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})
(def delete-order
  {:name ::order-delete

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           db (.delete @orderdb (:id tx-data))]
       (if (nil? db)
         (assoc context :response {:status 404 :body "No such order"})
         (assoc context :response {:status 200 :body "Success"}))))
   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})
