(ns rtm-clj.test.core
  (:use [rtm-clj.core])
  (:use [clojure.test]))

(deftest lookup-help
  (is (= help (lookup-command "help"))))
