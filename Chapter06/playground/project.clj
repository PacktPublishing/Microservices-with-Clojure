(defproject playground "0.1.0-SNAPSHOT"
  :description "Playground Project"
  :url "http://example.com/playground"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :main playground.core
  :source-paths ["src/clj"]
  :java-source-paths ["src/jvm"]
  :test-paths ["test/clj" "test/jvm"]
  :resource-paths ["resources" "conf"]
  :plugins [[:lein-codox "0.10.3"]
            ;; Code Coverage
            [:lein-cloverage "1.0.9"]
            ;; Style Checks
            [:jonase/eastwood "0.2.3"]
            [:lein-bikeshed "0.4.1"]
            [:lein-kibit "0.1.3"]
            ;; Unit test docs
            [test2junit "1.2.2"]]
  :codox {:namespaces :all}
  :test2junit-output-dir "target/test-reports"
  :profiles {:provided {:dependencies [[org.clojure/tools.reader "0.10.0"]
                                       [org.clojure/tools.nrepl "0.2.12"]]}
             :uberjar {:aot :all :omit-source true}
             :doc {:dependencies [[codox-theme-rdash "0.1.1"]]
                   :codox {:metadata {:doc/format :markdown}
                           :themes [:rdash]}}
             :dev {:resource-paths ["resources" "conf"]
                   :jvm-opts ["-Dconf=conf/conf.edn"]}
             :debug {:jvm-opts ["-server" (str "-agentlib:jdwp=transport=dt_socket,"
                                               "server=y,address=8000,suspend=n")]}})
