(ns helping-hands.auth.persistence
  "Persistence Implementation for Auth Service"
  (:require [agynamix.roles :as r]
            [cheshire.core :as jp])
  (:import [java.security MessageDigest]))

(defn get-hash
  "Creates a MD5 hash of the password"
  [creds]
  (.. (MessageDigest/getInstance "MD5")
      (digest (.getBytes creds "UTF-8"))))

(def userdb
  ;; Used ony for demonstration
  ;; TODO Persist in an external database
  (atom
   {:secret nil
    :roles {"hh/superadmin" "*"
            "hh/admin" "hh:*"
            "hh/notify" #{"hh:notify" "notify/alert"}
            "notify/alert" #{"notify:email" "notify:sms"}}
    :users {"hhuser" {:pwd (get-hash "hhuser")
                      :roles #{"hh/notify"}}
            "hhadmin" {:pwd (get-hash "hhadmin")
                       :roles #{"hh/admin"}}
            "superadmin" {:pwd (get-hash "superadmin")
                          :roles #{"hh/superadmin"}}}}))

(defn has-access?
  "Checks for relevant permission"
  [uid perms]
  (r/has-permission?
   (-> @userdb :users (get uid))
   :roles :permissions perms))

(defn init-db
  "Initializes the roles for permission framework"
  []
  (r/init-roles (:roles @userdb))
  userdb)
