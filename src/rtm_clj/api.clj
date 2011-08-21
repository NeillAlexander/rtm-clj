;; This contains the functions that actually call the Remember the Milk API.
;; The API is REST based, so it uses clj-http.
(ns rtm-clj.api

  (:require
   [clj-http.client :as http]
   [clojure.xml :as xml])

  (:import
   [java.security MessageDigest]
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
(def *token* (atom nil))

;; Helper functions to store the state away in the atoms.
(defn get-token
  []
  @*token*)

(defn set-token!
  [token]
  (reset! *token* token))

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
  {:api-key @*api-key* :shared-secret @*shared-secret* :token (get-token)})

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
           (set-token! (:token state))
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
      (:body (http/get url)))
    (println "Shared secret and / or api key not set")))

;; The responses that come back from RTM are xml. This converts into an
;; xml structure so that we can parse it.
(defn- to-xml
  "Convert the string to xml"
  [s]
  (let [input-stream (ByteArrayInputStream. (.getBytes s))]
    (xml/parse input-stream)))

;; # The Actual RTM Methods
;; These are the top-level api functions, corresponding (mostly) to
;; the methods defined
;; [here](http://www.rememberthemilk.com/services/api/request.rest.rtm)

;; The echo method just echos back the request params. Good for testing
;; basic connectivity.
;; Returns the full response map from clj-http.
(defn rtm-test-echo
  "Calls the rtm.test.echo method with the specified params"
  ([]
     (rtm-test-echo {"dummy" "value"}))
  ([param-map]
     (call-api "rtm.test.echo" param-map)))

(defn- parse-response
  [response tag-name]
  (when response
    (for [x (xml-seq (to-xml response)) :when (= tag-name (:tag x))]
      (first (:content x)))))

;; Returns a frob which is required in the call to authenticate the
;; user.
(defn rtm-auth-getFrob
  "Calls the rtm.auth.getFrob method"
  []
  (first (parse-response (call-api "rtm.auth.getFrob" {}) :frob)))

;; Generates the url that the user needs to access in order to grant
;; access for the client to access their account, and launches the
;; browser. Returns the frob.
(defn request-authorization
  "See http://www.rememberthemilk.com/services/api/authentication.rtm"
  []
  (if-let [frob (rtm-auth-getFrob)]
    (do
      (if-let [url (build-rtm-url {"perms" "delete", "frob" frob} *auth-url-base*)]
        (.browse (java.awt.Desktop/getDesktop) (URI. url)))
      frob)))

;; Puts in the call to the api for an auth token, which will be available
;; if the user has authorized access
;; can do this
;; (def token (rtm-auth-checkToken (rtm-auth-getToken (request-authorization))))
;; if that is truthy, then we're in
(defn rtm-auth-getToken
  [frob]
  (first (parse-response (call-api "rtm.auth.getToken" {"frob" frob}) :token)))

;; If the token is valid, then it is returned, otherwise nil
(defn rtm-auth-checkToken
  "Checks if the token is still valid. Returns the token if it is valid, otherwise
returns nil"
  [token]
  (first (parse-response (call-api "rtm.auth.checkToken" {"auth_token" token}) :token)))

(defn have-valid-token?
  []
  (let [token (get-token)]
    (rtm-auth-checkToken token)))


;; Returns the lists for the user as a sequence in the following format:
;; ({:id "list_id" :name "list_name"} {:id "another_list" :name "another list name"} etc)
(defn rtm-lists-getList
  "Returns the lists for the user"
  []
  (if-let [list-xml (to-xml (call-api "rtm.lists.getList" {"auth_token" (get-token)}))]
    (for [x (xml-seq list-xml) :when (= :list (:tag x))]
      (:attrs x))))

;; Returns all the tasks, or the tasks for a particular list. Supports the RTM
;; search filters. By default it uses status:incomplete to only return incomplete
;; tasks
;; NB: it only returns a sub-set of the data currently. I may add more in as I go along...
(defn rtm-tasks-getList
  "Gets all the tasks for a particular list, or all tasks if not list-id provided"
  ([list-id]
     (rtm-tasks-getList list-id "status:incomplete"))
  ([list-id list-filter]
     (if-let [xml (to-xml (call-api "rtm.tasks.getList" {"auth_token" (get-token), "list_id" list-id, "filter" list-filter}))]
       (for [task-series (xml-seq xml) :when (= :taskseries (:tag task-series))]
         (for [task (:content task-series) :when (= :task (:tag task))]
           (assoc (:attrs task-series) :due (:due (:attrs task))))))))
