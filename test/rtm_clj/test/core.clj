(ns rtm-clj.test.core
  (:use [rtm-clj.core])
  (:use [clojure.test]))

(deftest lookup-help
  (is (= help (lookup-command "help"))))

(defn dummy-command
  "For testing"
  []
  1)

(register-command dummy-command "dummy")

;; once this passes it will be easier to test from repl
(deftest call-handles-unsplit-string
  (is (= 1 (call "dummy command call")))
  (is (= 1 (call "dummy"))))
