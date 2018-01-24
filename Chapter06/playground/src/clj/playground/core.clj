(ns playground.core
  "Core Playground Namespace"
  (:gen-class))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "| Hello, World!"))

(defn -main
  "Entry point for application"
  [& args]
  (foo (or (first args) "Noname")))
