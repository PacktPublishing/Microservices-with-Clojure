(ns helping-hands.consumer.config
  "Defines Configuration for the Service"
  (:require [omniconf.core :as cfg]))

(defn init-config
  "Initializes the configuration"
  [{:keys [cli-args quit-on-error] :as params
    :or {cli-args [] quit-on-error true}}]
  ;; define the configuration
  (cfg/define
    {:conf {:type :file
            :required true
            :verifier omniconf.core/verify-file-exists
            :description "MECBOT configuration file"}
     :datomic
     {:nested
      {:uri {:type :string
             :default "datomic:mem//consumer"
             :description "Datomic URI for Consumer Database"}}}})
  ;; load properties to pick -Dconf for the config file
  (cfg/populate-from-properties quit-on-error)
  ;; Configuration file specified as
  ;; Environment variable CONF or JVM Opt -Dconf
  (when-let [conf (cfg/get :conf)]
    (cfg/populate-from-file conf quit-on-error))
  ;; like- :some-option => (java -Dsome-option=...)
  ;; reload JVM args to overwrite configuration file params
  (cfg/populate-from-properties quit-on-error)
  ;; like- :some-option => -some-option
  (cfg/populate-from-cmd cli-args quit-on-error)
  ;; like- :some-option => SOME_OPTION
  (cfg/populate-from-env quit-on-error)
  ;; Verify the configuration
  (cfg/verify :quit-on-error quit-on-error))

(defn get-config
  "Gets the specified config param value"
  [& args]
  (apply cfg/get args))
