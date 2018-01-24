(ns helping-hands.alert.core
  "Initializes Helping Hands Alert Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [postal.core :as postal]
            [helping-hands.alert.persistence :as p]
            [io.pedestal.interceptor.chain :as chain])
  (:import [java.io IOException]
           [java.util UUID]))

;; --------------------------------
;; Validation Interceptors
;; --------------------------------

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (-> context :request :form-params)]
    (if (and (not (empty? params))
             (not (empty? (:to params)))
             (not (empty? (:body params))))
      (let [to-val (map s/trim (s/split (:to params) #","))]
        (assoc context :tx-data (assoc params :to to-val)))
      (chain/terminate
       (assoc context
              :response {:status 400
                         :body "Both to and body are required"})))))

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

(def send-email
  {:name ::send-email

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           msg (into {} (filter (comp some? val)
                                {:from "admin@helpinghands.com"
                                 :to (:to tx-data)
                                 :cc (:cc tx-data)
                                 :subject (:subject tx-data)
                                 :body (:body tx-data)}))
           result (postal/send-message
                   {:host "smtp.gmail.com"
                    :port 465
                    :ssl true
                    :user "admin@helpinghands.com"
                    :pass "resetme"}
                   msg)]
       ;; send email
       (assoc context :response
              {:status 200
               :body (jp/generate-string result)})))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def send-sms
  {:name ::send-sms

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)]
       ;; TODO
       ;; Send SMS
       (assoc context :response {:status 200 :body "SUCCESS"})))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})
