(ns rtm-clj.test.xml
  (:require [rtm-clj.xml :as xml])
  (:use [clojure.test]))

(def test-xml (slurp "test/rtm_clj/test/list.xml"))
(def task-series-xml (slurp "test/rtm_clj/test/task-series.xml"))

(def simple-xml "<root><value>123</value></root>")

(def error-xml "<?xml version='1.0' encoding='UTF-8'?><rsp stat=\"fail\"><err code=\"96\" msg=\"Invalid signature\"/></rsp>")

(def undoable-xml "<?xml version='1.0' encoding='UTF-8'?><rsp stat=\"ok\"><transaction id=\"4641053630\" undoable=\"1\"/><list id=\"11345094\"><taskseries id=\"129979257\" created=\"2011-09-04T07:13:45Z\" modified=\"2011-09-04T07:13:52Z\" name=\"test\" source=\"api\" url=\"\" location_id=\"\"><tags/><participants/><notes/><task id=\"201963277\" due=\"\" has_due_time=\"0\" added=\"2011-09-04T07:13:45Z\" completed=\"\" deleted=\"2011-09-04T07:13:52Z\" priority=\"N\" postponed=\"0\" estimate=\"\"/></taskseries></list></rsp>")

(def not-undoable-xml "<?xml version='1.0' encoding='UTF-8'?><rsp stat=\"ok\"><transaction id=\"4641053630\" undoable=\"0\"/><list id=\"11345094\"><taskseries id=\"129979257\" created=\"2011-09-04T07:13:45Z\" modified=\"2011-09-04T07:13:52Z\" name=\"test\" source=\"api\" url=\"\" location_id=\"\"><tags/><participants/><notes/><task id=\"201963277\" due=\"\" has_due_time=\"0\" added=\"2011-09-04T07:13:45Z\" completed=\"\" deleted=\"2011-09-04T07:13:52Z\" priority=\"N\" postponed=\"0\" estimate=\"\"/></taskseries></list></rsp>")

(deftest test-parse-response
  (is (= "123" (first (xml/parse-response simple-xml :value)))))

(deftest test-parse-undoable
  (is (= {:transaction-id "4641053630" :timeline "123"} (xml/parse-undoable undoable-xml "123")))
  (is (nil? (xml/parse-undoable not-undoable-xml "123"))))
