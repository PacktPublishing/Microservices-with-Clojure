(defproject helping-hands "0.0.1-SNAPSHOT"
  :description "Helping Hands Application"
  :url "https://www.packtpub.com/application-development/microservices-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.pedestal/pedestal.service "0.5.3"]

                 ;; Remove this line and uncomment one of the next lines to
                 ;; use Immutant or Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.5.3"]
                 ;; [io.pedestal/pedestal.immutant "0.5.3"]
                 ;; [io.pedestal/pedestal.tomcat "0.5.3"]

                 [ch.qos.logback/logback-classic "1.1.8" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.22"]
                 [org.slf4j/jcl-over-slf4j "1.7.22"]
                 [org.slf4j/log4j-over-slf4j "1.7.22"]]
  :min-lein-version "2.0.0"
  :source-paths ["src/clj"]
  :java-source-paths ["src/jvm"]
  :test-paths ["test/clj" "test/jvm"]
  :resource-paths ["config", "resources"]
  :plugins [[:lein-codox "0.10.3"]
            ;; Code Coverage
            [:lein-cloverage "1.0.9"]
            ;; Unit test docs
            [test2junit "1.2.2"]]
  :codox {:namespaces :all}
  :test2junit-output-dir "target/test-reports"
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
  ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.5"]]
  :profiles {:provided {:dependencies [[org.clojure/tools.reader "0.10.0"]
                                       [org.clojure/tools.nrepl "0.2.12"]]}
             :dev {:aliases {"run-dev" ["trampoline" "run" "-m" "helping-hands.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.3"]]
                   :resource-paths ["config", "resources"]
                   :jvm-opts ["-Dconf=config/conf.edn"]}
             :uberjar {:aot [helping-hands.server]}
             :doc {:dependencies [[codox-theme-rdash "0.1.1"]]
                   :codox {:metadata {:doc/format :markdown}
                           :themes [:rdash]}}
             :debug {:jvm-opts
                     ["-server" (str "-agentlib:jdwp=transport=dt_socket,"
                                     "server=y,address=8000,suspend=n")]}}
  :main ^{:skip-aot true} helping-hands.server)

