(ns rtm-clj.test.xml
  (:require [rtm-clj.xml :as xml])
  (:use [clojure.test]))

(def test-xml (slurp "test/rtm_clj/test/list.xml"))
(def task-series-xml (slurp "test/rtm_clj/test/task-series.xml"))

(def simple-xml "<root><value>123</value></root>")

(def error-xml "<?xml version='1.0' encoding='UTF-8'?><rsp stat=\"fail\"><err code=\"96\" msg=\"Invalid signature\"/></rsp>")

(deftest test-parse-response
  (is (= "123" (first (xml/parse-response simple-xml :value)))))



