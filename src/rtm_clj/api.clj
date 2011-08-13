(ns rtm-clj.api
  (:require [clj-http.client :as http])
  (:import [java.security MessageDigest]
           [java.io ByteArrayInputStream]))

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

(defn- shared-secret-set?
  []
  (not (empty? @*shared-secret*)))

(defn- api-key-set?
  []
  (not (empty? @*api-key*)))

;; see http://www.rememberthemilk.com/services/api/authentication.rtm
(defn- sign-params
  "This does the signing that RTM api requires"
  [param-map]
  (if (shared-secret-set?)
    (let [sorted-map (sort param-map)]
      (md5sum (str @*shared-secret* (build-params sorted-map "" ""))))))

(defn build-rtm-url
  "Builds the url to hit the rest service"
  [method param-map]
  (let [all-params (assoc param-map "method" method "api_key" @*api-key*)
        api-sig (sign-params all-params)]
    (str *base-url* (build-params all-params) "&api_sig=" api-sig)))

(defn- call-api
  [method param-map]
  (if (and (shared-secret-set?) (api-key-set?))
    (let [url (str (build-rtm-url method param-map))]
      (http/get url))
    (println "Shared secret and / or api key not set")))

(defn- to-xml
  "Convert the string to xml"
  [s]
  (let [input-stream (ByteArrayInputStream. (.getBytes s))]
    (clojure.xml/parse input-stream)))

;;-------------------------------------------------------------------------
;; The actual rtm methods
;;-------------------------------------------------------------------------

;; see http://www.rememberthemilk.com/services/api/request.rest.rtm
(defn rtm-test-echo
  "Calls the rtm.test.echo method with the specified params"
  [param-map]
  (call-api "rtm.test.echo" param-map))

(defn rtm-auth-getFrob
  "Calls the rtm.auth.getFrob method"
  []
  ;; make sure the shared secret and api key are set
  (if-let [response (:body (call-api "rtm.auth.getFrob" {}))]
    (first (:content (first (:content (to-xml response)))))))

