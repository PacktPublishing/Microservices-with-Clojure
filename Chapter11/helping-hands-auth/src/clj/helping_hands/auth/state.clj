(ns helping-hands.auth.state
  "Initializes State for Auth Service"
  (:require [mount.core :refer [defstate] :as mount]
            [helping-hands.auth.jwt :as jwt]
            [helping-hands.auth.persistence :as p]))

(defstate auth-db
  :start (let [db (p/init-db)]
           ;; if key does not exist, initialize one
           ;; and update the database with :secret key
           (if-not (:secret @db)
             (swap! db #(assoc % :secret (jwt/get-secret))) @db))
  :stop nil)
