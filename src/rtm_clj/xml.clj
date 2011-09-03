(ns rtm-clj.xml
  (:require [rtm-clj.utils :as utils]
   [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.contrib.zip-filter :as zf]
            [clojure.contrib.zip-filter.xml :as zfx])
  (:import [java.io ByteArrayInputStream]))

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

;; Quick way to convert xml structure to a flatter map structure.
(defn- assoc-attributes
  "Takes a map m, zip location loc, an attribute name att, and more attributes more.
Associates the attribute into the map using the attribute from the xml as the value."
  [m loc att & more]
  (let [new-m (assoc m att (zfx/xml1-> loc (zfx/attr att)))]
    (if (seq more)
      (recur new-m loc (first more) (rest more))
      new-m)))

(defn- create-note-map
  [loc]
  (assoc-attributes {} loc :id :created :modified :title))

(defn- extract-notes
  "Assocs the notes into the map."
  [m task-series-loc]
  (let [notes (map create-note-map (zfx/xml-> task-series-loc :notes :note))]
    (assoc m :notes notes)))

(defn- create-task-map
  "Creates a flat map of the key attributes from the xml, representing a task."
  [task-loc]
  (let [task-series-loc (zip/up task-loc)]
    ;; create task-series-id to avoid clash with task id, that way can have flat map
    (let [task-map
          (-> (assoc-attributes {:task-series-id (zfx/xml1-> task-series-loc (zfx/attr :id))}
                                task-series-loc :created :modified :name :source :url :location_id)
              (assoc-attributes task-loc :id :due :has_due_time :added :completed
                                :deleted :priority :postponed :estimate)
              (extract-notes task-series-loc)
              (assoc :list-id (zfx/xml1-> (zip/up task-series-loc) (zfx/attr :id))))]
      (utils/debug (str "task-map: " task-map))
      task-map)))


(defn parse-task-series-response
  "Creates a flat map of the key task series data. Path specifies the zip-filter path
to the task tag"
  ([xml]
     (parse-task-series-response xml :tasks :list :taskseries :task))
  ([xml & path]
     (utils/debug (str "xml: " xml))
     (let [zipped (zip/xml-zip (to-xml xml))]
       (map create-task-map (apply zfx/xml-> zipped path)))))


(defn parse-add-task-response
  [xml]
  (parse-task-series-response xml :list :taskseries :task))

;; example of a task-series-response
(comment
  {:notes ({:title "http://www.rememberthemilk.com/services/api/", :modified "2011-06-04T05:07:52Z", :created "2011-06-04T05:07:52Z", :id "22967071"}), :list-id "11361634", :estimate "", :name "Remember The Milk - Services / API", :postponed "0", :has_due_time "0", :location_id "", :added "2011-06-04T05:07:51Z", :task-series-id "119659454", :url "", :created "2011-06-04T05:07:51Z", :completed "", :modified "2011-07-03T10:58:35Z", :due "", :source "email", :id "183302555", :deleted "", :priority "1"})

(defn parse-error
  "Returns a map of the error code or nil if there was no error"
  [xml]
  (let [zipped (zip/xml-zip (to-xml xml))
        stat (zfx/xml1-> zipped (zfx/attr :stat))]
    (if (= "fail" stat)
      (let [err-loc (zfx/xml1-> zipped :err)]
        (assoc-attributes {} err-loc :code :msg))
      nil)))
