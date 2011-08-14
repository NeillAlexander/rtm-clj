(ns rtm-clj.api
  (:require [clj-http.client :as http])
  (:import [java.security MessageDigest]
           [java.io ByteArrayInputStream]))

;; constant
(def *api-url* "http://api.rememberthemilk.com/services/rest/?")
(def *auth-url-base* "http://www.rememberthemilk.com/services/auth/?")
(def *state-file* (str (System/getenv "HOME") "/.rtm-clj"))

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

(defn get-state
  []
  {:api-key @*api-key* :shared-secret @*shared-secret*})

(defn save-state
  "Save the api key and shared secret to a file"
  ([]
     (save-state *state-file*))
  ([f]
     (spit f (get-state))))

(defn load-state!
  "Tries to set up the api key and shared secret from a file"
  ([]
     (load-state! *state-file*))
  ([f]
     (try
       (if-let [ state (read-string (slurp f))]
         (do
           (set-api-key! (:api-key state))
           (set-shared-secret! (:shared-secret state))
           state))
       (catch Exception e
         nil))))

;; url stuff

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
  ([param-map]
     (build-rtm-url param-map *api-url*))
  ([param-map base-url]
     (let [all-params (assoc param-map "api_key" @*api-key*)
           api-sig (sign-params all-params)]
       (str base-url (build-params all-params) "&api_sig=" api-sig))))

(defn- call-api
  [method param-map]
  (if (and (shared-secret-set?) (api-key-set?))
    (let [param-map-with-method (assoc param-map "method" method)
          url (str (build-rtm-url param-map-with-method))]
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

;; need to generate the url to redirect the user to authenticate
(defn authenticate-user
  "See http://www.rememberthemilk.com/services/api/authentication.rtm"
  []
  (if-let [frob (rtm-auth-getFrob)]
    (build-rtm-url {"perms" "delete", "frob" frob} *auth-url-base*)))
