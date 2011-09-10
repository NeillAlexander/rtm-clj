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
     (let [line (str/trim (read-line))]
       (if (vf line)
         (recur s vf)
         line))))

(def *debug-on* (atom false))

(defn switch-debug-on! [bool]
  (reset! *debug-on* bool))

(defn debug [s]
  (when @*debug-on*
    (do
      (println)
      (println s))))

(defn make-map-comparator
  "Makes a comparator that can compare values determined by the keys. adaptor is applied to the result of the comparator,
should be + for ascending or - for descending (or something else if you want to be funky!)"
  ([f & keys]
     (let [selector (apply comp (reverse keys))]    
       (fn [map-a map-b]
         (f (compare (selector map-a) (selector map-b)))))))

(defn make-combined-comparator
  "Makes a comparator that strings all the provided comparators together. Enables to do multi-level sorting."
  [comparator & comparators]
  (fn [x y]
    (loop [comp-fn comparator
           remaining comparators]
      (let [result (comp-fn x y)]
        (if (= 0 result)
          (if (seq remaining)
            (recur (first remaining) (rest remaining))
            result)
          result)))))

(defn make-combined-key-comparator
  "Makes a combined comparator that will sort by each of the keys in order to differentiate."
  [f & keys]
  (let [comparators (map #(make-map-comparator f %) keys)]
    (apply make-combined-comparator comparators)))
