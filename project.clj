(defproject rtm-clj "0.1.0-SNAPSHOT"
  :description "A command line interface for Remember the Milk."
  :dev-dependencies [[lein-marginalia "0.6.0"]]
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [clj-http "0.1.3"]
                 [swank-clojure/swank-clojure "1.3.2"]
                 [clojure-contrib "1.2.0" ]]
  :main rtm-clj.core)

