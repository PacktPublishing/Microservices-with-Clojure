(ns helping-hands.consumer.state
  "Initializes State for Consumer Service"
  (:require [mount.core :refer [defstate] :as mount]
            [helping-hands.consumer.persistence :as p]))

(defstate consumerdb
  :start (p/create-consumer-database)
  :stop (.stop consumerdb))
