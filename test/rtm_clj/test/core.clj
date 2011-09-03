(ns rtm-clj.test.core
  (:use [rtm-clj.core])
  (:use [clojure.test]))

(deftest lookup-help
  (is (= help (lookup-command "help"))))

(deftest test-arity-check-fn
  (is (= true ((arity-check []) 0)))
  (is (= true ((arity-check [:x]) 1)))
  (is (= true ((arity-check ['& :args]) 0)))
  (is (= true ((arity-check ['& :args]) 1)))
  (is (= true ((arity-check [:x '& :args]) 1)))
  (is (= true ((arity-check [:x '& :args]) 2)))
  (is (= false ((arity-check []) 1)))
  (is (= false ((arity-check [:x]) 2))))

(deftest var-args-arity
  "Tests that arity checks handles & args"
  (is (= true (arity-matches-args echo ["hello"])))
  (is (= true (arity-matches-args echo ["hello there"])))
  (is (= true (arity-matches-args echo ["hello there everyone"])))
  (is (= true (arity-matches-args echo ["hello there everyone in the world"]))))

(def test-xml (slurp "test/rtm_clj/test/list.xml"))
(def task-series-xml (slurp "test/rtm_clj/test/task-series.xml"))

(deftest test-parse-task-series
  )
