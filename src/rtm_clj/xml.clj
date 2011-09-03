(ns rtm-clj.xml
  (:require [clojure.xml :as xml]
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

(defn- zip
  [s]
  (zip/xml-zip (to-xml s)))

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
    (-> (assoc-attributes {:task-series-id (zfx/xml1-> task-series-loc (zfx/attr :id))}
                          task-series-loc :created :modified :name :source :url :location_id)
        (assoc-attributes task-loc :id :due :has_due_time :added :completed
                          :deleted :priority :postponed :estimate)
        (extract-notes task-series-loc))))

(defn parse-task-series-response
  "Creates a flat map of the key task series data"
  [xml]
  (let [zipped (zip xml)]
    (map create-task-map (zfx/xml-> zipped :tasks :list :taskseries :task))))

