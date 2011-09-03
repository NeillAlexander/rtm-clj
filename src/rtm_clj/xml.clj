(ns rtm-clj.xml
  (:require [clojure.xml :as xml])
  (:import [java.io ByteArrayInputStream]))

(defn- extract-notes
  "Returns the notes data from the task series xml"
  [task-series]
  (for [notes (:content task-series) :when (= :notes (:tag notes))]
    (for [note (:content notes) :when (= :note (:tag note))]
      (:attrs note))))

;; The responses that come back from RTM are xml. This converts into an
;; xml structure so that we can parse it.
(defn to-xml
  "Convert the string to xml"
  [s]
  (let [input-stream (ByteArrayInputStream. (.getBytes s))]
    (xml/parse input-stream)))

;; Simple function that parses the response xml string and returns the
;; value of the tag
(defn parse-response
  [response tag-name]
  (when response
    (for [x (xml-seq (to-xml response)) :when (= tag-name (:tag x))]
      (first (:content x)))))

;; Parses any response that rturns a task series
(defn parse-task-series-response
  [xml]
  (for [task-series (xml-seq xml) :when (= :taskseries (:tag task-series))]
    (for [task (:content task-series) :when (= :task (:tag task))]
      (assoc (:attrs task-series) :due (:due (:attrs task)) :notes (extract-notes task-series)))))
