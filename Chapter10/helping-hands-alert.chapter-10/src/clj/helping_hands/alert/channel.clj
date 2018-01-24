(ns helping-hands.alert.channel
  "Initializes Helping Hands Alert Channel Consumer"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [helping-hands.alert.config :as conf]
            [postal.core :as postal])
  (:import [java.util Collections Properties]
           [org.apache.kafka.common.serialization
            LongDeserializer StringDeserializer]
           [org.apache.kafka.clients.consumer
            Consumer ConsumerConfig KafkaConsumer]))

(defn create-kafka-consumer
  "Creates a new Kafka Consumer"
  []
  (let [props (doto (Properties.)
                (.putAll (conf/get-config [:kafka]))
                (.put ConsumerConfig/KEY_DESERIALIZER_CLASS_CONFIG
                      (.getName LongDeserializer))
                (.put ConsumerConfig/VALUE_DESERIALIZER_CLASS_CONFIG
                      (.getName StringDeserializer)))
        consumer (KafkaConsumer. props)
        _ (.subscribe consumer (Collections/singletonList
                                (get (conf/get-config [:kafka]) "topic")))]
    consumer))

(defn consume-records
  "Consume the records using given consumer"
  [consumer result]
  (while true
    (doseq [record (.poll consumer 1000)]
      (try
        (let [rmsg (jp/parse-string (.value record))
              msg (into {} (filter (comp some? val)
                                   {:from (conf/get-config [:alert :from])
                                    :to (get rmsg "to" (conf/get-config [:alert :to]))
                                    :cc (rmsg "cc")
                                    :subject (rmsg "subject")
                                    :body (rmsg "body")}))
              result (postal/send-message
                      {:host (conf/get-config [:alert :host])
                       :port (conf/get-config [:alert :port])
                       :ssl (conf/get-config [:alert :ssl])
                       :user (conf/get-config [:alert :user])
                       :pass (conf/get-config [:alert :creds])}
                      msg)])
        (catch Exception e
          (log/error "Failed to send email" e)))
      (swap! result conj record))
    (Thread/sleep 5000)))

(defn capture-records
  "Consume the records using given consumer"
  [consumer result]
  (while true
    (doseq [record (.poll consumer 1000)]
      (swap! result conj record))
    (Thread/sleep 5000)))
