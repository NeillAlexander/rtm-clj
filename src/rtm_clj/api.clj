;; This contains the functions that actually call the Remember the Milk API.
;; The API is REST based, so it uses clj-http.
(ns rtm-clj.api
  (:require [clj-http.client :as http]
            [clojure.zip :as zip]
            [clojure.xml :as xml])
  (:use [clojure.contrib.zip-filter.xml])
  (:import [java.security MessageDigest]
           [java.io ByteArrayInputStream]
           [java.net URI]))

;; # Constants
(def *api-url* "http://api.rememberthemilk.com/services/rest/?")
(def *auth-url-base* "http://www.rememberthemilk.com/services/auth/?")

;; At some point I may make this configurable. This is where the
;; state is stored.
(def *state-file* (str (System/getenv "HOME") "/.rtm-clj"))

;; # State

;; These are the 2 things needed to authenticate with RTM.
(def *api-key* (atom ""))
(def *shared-secret* (atom ""))

;; Helper functions to store the state away in the atoms.
(defn set-api-key!
  "Sets the key for the session"
  [key]
  (reset! *api-key* key))

(defn set-shared-secret!
  "Sets the shared secret for the session"
  [secret]
  (reset! *shared-secret* secret))

;; And a helper function to get the current state.
(defn get-state
  []
  {:api-key @*api-key* :shared-secret @*shared-secret*})

;; ## Persistence
;; Store the state to a file, using the default location.
(defn save-state
  "Save the api key and shared secret to a file"
  ([]
     (save-state *state-file*))
  ([f]
     (spit f (get-state))))

;; Load the state up again. This is called on startup. Note
;; the exclamation mark, due to the fact that it overrides
; the current state.
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

;; # URL Building

;; Accepts a map, and converts it into key value pairs for the url
(defn- build-params
  "Builds key=value&key=value&key=value to append onto url"
  ([param-map]
     (build-params param-map "=" "&"))
  ;; this is used when signing parameters, when the = and & is stripped out.
  ([param-map key-val-separator param-separator]
     (apply str (drop-last (interleave (map #(str (first %) key-val-separator (second %)) param-map) (repeat param-separator))))))

(defn- md5sum
  [s]
  (.toString (BigInteger. 1 (.digest (MessageDigest/getInstance "MD5") (.getBytes s))) 16))

;; Building up the vocabulary...
(defn- shared-secret-set?
  []
  (not (empty? @*shared-secret*)))

(defn- api-key-set?
  []
  (not (empty? @*api-key*)))

;; See [RTM Authentication](http://www.rememberthemilk.com/services/api/authentication.rtm)
;; documentation.
(defn- sign-params
  "This does the signing that RTM api requires"
  [param-map]
  (if (shared-secret-set?)
    (let [sorted-map (sort param-map)]
      (md5sum (str @*shared-secret* (build-params sorted-map "" ""))))))

;; Builds the url, with the api and signature parameters correctly applied.
(defn build-rtm-url
  "Builds the url to hit the rest service"
  ([param-map]
     (build-rtm-url param-map *api-url*))
  ([param-map base-url]
     (let [all-params (assoc param-map "api_key" @*api-key*)
           api-sig (sign-params all-params)]
       (str base-url (build-params all-params) "&api_sig=" api-sig))))

;; Abstracts out the the REST call.
;; method is the name of the RTM method.
;; The param-map contains the key/value pairs for the http request params.
(defn- call-api
  [method param-map]
  (if (and (shared-secret-set?) (api-key-set?))
    (let [param-map-with-method (assoc param-map "method" method)
          url (str (build-rtm-url param-map-with-method))]
      (http/get url))
    (println "Shared secret and / or api key not set")))

;; The responses that come back from RTM are xml. This converts into an
;; xml structure so that we can parse it.
(defn- to-xml
  "Convert the string to xml"
  [s]
  (let [input-stream (ByteArrayInputStream. (.getBytes s))]
    (zip/xml-zip (xml/parse input-stream))))

;; Parse the xml extracting the relevant information
(defn extract-xml-data
  [xml-data & preds]
  (apply xml-> xml-data preds))

;; # The Actual RTM Methods
;; These are the top-level api functions, corresponding (mostly) to
;; the methods defined
;; [here](http://www.rememberthemilk.com/services/api/request.rest.rtm)

;; The echo method just echos back the request params. Good for testing
;; basic connectivity.
;; Returns the full response map from clj-http.
(defn rtm-test-echo
  "Calls the rtm.test.echo method with the specified params"
  [param-map]
  (call-api "rtm.test.echo" param-map))

;; Returns a frob which is required in the call to authenticate the
;; user.
(defn rtm-auth-getFrob
  "Calls the rtm.auth.getFrob method"
  []
  (if-let [response (:body (call-api "rtm.auth.getFrob" {}))]
    (first (extract-xml-data (to-xml response) :frob text))))

;; Generates the url that the user needs to access in order to grant
;; access for the client to access their account, and launches the
;; browser
(defn request-authorization
  "See http://www.rememberthemilk.com/services/api/authentication.rtm"
  []
  (if-let [frob (rtm-auth-getFrob)]
    (if-let [url (build-rtm-url {"perms" "delete", "frob" frob} *auth-url-base*)]
      (do
        (println (str "Opening browser at " url))
        (.browse (java.awt.Desktop/getDesktop) (URI. url))
        frob))))

;; Puts in the call to the api for an auth token, which will be available
;; if the user has authorized access
(defn rtm-auth-getToken
  [frob]
  )
