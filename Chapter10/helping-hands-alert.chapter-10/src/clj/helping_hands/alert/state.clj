(ns helping-hands.alert.state
  "Initializes State for Alert Service"
  (:require [mount.core :refer [defstate] :as mount]
            [helping-hands.alert.channel :as c]))

(defstate alert-consumer
  :start (c/create-kafka-consumer)
  :stop (.close alert-consumer))
