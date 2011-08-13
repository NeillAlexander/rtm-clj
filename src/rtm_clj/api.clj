(ns rtm-clj.api
  (:require [clj-http.client :as http])
  (:import [java.security MessageDigest]))

;; constant
(def *base-url* "http://api.rememberthemilk.com/services/rest/?")

;; state
(def *api-key* (atom ""))
(def *shared-secret* (atom ""))

(defn set-api-key!
  "Sets the key for the session"
  [key]
  (reset! *api-key* key))

(defn set-shared-secret!
  "Sets the shared secret for the session"
  [secret]
  (reset! *shared-secret* secret))

(defn- build-params
  "Builds key=value&key=value&key=value to append onto url"
  ([param-map]
     (build-params param-map "=" "&"))
  ([param-map key-val-separator param-separator]
     (apply str (drop-last (interleave (map #(str (first %) key-val-separator (second %)) param-map) (repeat param-separator))))))

(defn- md5sum
  [s]
  (.toString (BigInteger. 1 (.digest (MessageDigest/getInstance "MD5") (.getBytes s))) 16))

;; see http://www.rememberthemilk.com/services/api/authentication.rtm
(defn- sign-params
  "This does the signing that RTM api requires"
  [param-map]
  (if-not (empty? @*shared-secret*)
    (let [sorted-map (sort param-map)]
      (md5sum (str @*shared-secret* (build-params sorted-map "" ""))))))

(defn build-rtm-url
  "Builds the url to hit the rest service"
  [method]
  (str *base-url* "method=" method "&api_key=" @*api-key*))

(defn- call-api
  [method param-map])

;; see http://www.rememberthemilk.com/services/api/request.rest.rtm
(defn echo
  "Calls the rtm.test.echo method with the specified params"
  [param-map]
  (let [url (str (build-rtm-url "rtm.test.echo") "&" (build-params param-map))]
    (http/get url)))
