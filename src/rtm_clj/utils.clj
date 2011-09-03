(ns rtm-clj.utils
  (:require [clojure.string :as str]))

;; utility functions
(defmulti as-int class)

(defmethod as-int Number [v]
  (int v))

(defmethod as-int String [s]
  (try
    (Integer/parseInt s)
    (catch Exception e nil)))

(defmethod as-int :default [x] nil)

;; used twice, so factored out
(defn indexify
  "Creates a map using the supplied collection, where each key is a number starting at i and incrementing"
  ([coll]
     (indexify coll 1))
  ([coll i]
     (apply array-map (interleave (iterate inc i) coll))))

;; This function displays the prompt, reads input, and returns the full line
;; as a String. Note that it is parameterized so that it can be used to request
;; specific input from the user
;; It would probably be useful to add a validation function in here as well to
;; make it more general.
(defn prompt!
  "Displays the prompt for the user and reads input for stdin. Returns a vector
of the individual words that were entered."
  ([]
     (prompt! "rtm> "))
  ([s]
     (prompt! s str/blank?))
  ([s vf]
     (print s)
     (flush)
     (let [line (read-line)]
       (if (vf line)
         (recur s vf)
         line))))
