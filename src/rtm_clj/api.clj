;; This contains the functions that actually call the Remember the Milk API.
;; The API is REST based, so it uses clj-http.
(ns rtm-clj.api

  (:require
   [rtm-clj.utils :as utils]
   [rtm-clj.xml :as xml]
   [clj-http.client :as http])

  (:import
   [java.security MessageDigest]
   [java.net URLEncoder]))

;; # Constants
(def *api-url* "http://api.rememberthemilk.com/services/rest/?")
(def *auth-url-base* "http://www.rememberthemilk.com/services/auth/?")

;; # URL Building

;; Accepts a map, and converts it into key value pairs for the url
(defn- build-params
  ([request-params]
     (build-params request-params "=" "&" true))
  ([request-params key-val-separator param-separator encode?]
     (let [encode (if (true? encode?) #(URLEncoder/encode (str %) "UTF-8") #(str %))
           coded (for [[n v] request-params] (str (encode n) key-val-separator (encode v)))]
       (apply str (interpose param-separator coded)))))

(defn- md5sum
  [s]
  (format "%1$032x" (BigInteger. 1 (.digest (MessageDigest/getInstance "MD5") (.getBytes s)))))

;; Building up the vocabulary...
(defn shared-secret-set?
  [state]
  (not (empty? (:shared-secret state))))

(defn api-key-set?
  [state]
  (not (empty? (:api-key state))))

;; See [RTM Authentication](http://www.rememberthemilk.com/services/api/authentication.rtm)
;; documentation.
(defn- sign-params
  "This does the signing that RTM api requires"
  [state param-map]
  (if (shared-secret-set? state)
    (let [sorted-map (sort param-map)
          sig-string (str (:shared-secret state) (build-params sorted-map "" "" false))]
      (utils/debug (str "Signature string: " sig-string))
      (md5sum sig-string))))

;; Builds the url, with the api and signature parameters correctly applied.
(defn build-rtm-url
  "Builds the url to hit the rest service"
  ([state param-map]
     (build-rtm-url state param-map *api-url*))
  ([state param-map base-url]
     (utils/debug (str "Building url: " param-map base-url))
     (let [all-params (assoc param-map "api_key" (:api-key state))
           api-sig (sign-params state all-params)
           url (str base-url (build-params all-params) "&api_sig=" api-sig)]
       (utils/debug url)
       url)))

;; This should probably work differently. Doesn't really make sense for a general
;; api call, only in context of the command line.
(defn- check-for-error
  "A bit ugly, but it at least logs an error if the api call failed."
  [response-xml]
  (if-let [error (xml/parse-error response-xml)]
    (do
      (println (str "Error: " (:msg error)))
      nil)
    response-xml))

;; Abstracts out the the REST call.
;; method is the name of the RTM method.
;; The param-map contains the key/value pairs for the http request params.
(defn- call-api
  [state method param-map]
  (if (and (shared-secret-set? state) (api-key-set? state))
    (let [param-map-with-method (assoc param-map "method" method)
          url (str (build-rtm-url state param-map-with-method))]
      (check-for-error (:body (http/get url))))
    (println "Shared secret and / or api key not set")))

(defn- call-api-with-token
  ([state method]
     (call-api-with-token state method {}))
  ([state method param-map]
     (call-api state method (assoc param-map "auth_token" (:token state)))))

;; # The Actual RTM Methods
;; These are the top-level api functions, corresponding (mostly) to
;; the methods defined
;; [here](http://www.rememberthemilk.com/services/api/request.rest.rtm)

;; The echo method just echos back the request params. Good for testing
;; basic connectivity.
;; Returns the full response map from clj-http.
(defn rtm-test-echo
  "Calls the rtm.test.echo method with the specified params"
  ([state]
     (rtm-test-echo state {"dummy" "value"}))
  ([state param-map]
     (call-api state "rtm.test.echo" param-map)))

;; Returns a frob which is required in the call to authenticate the
;; user.
(defn rtm-auth-getFrob
  "Calls the rtm.auth.getFrob method"
  [state]
  (call-api state "rtm.auth.getFrob" {}))

;; Puts in the call to the api for an auth token, which will be available
;; if the user has authorized access
(defn rtm-auth-getToken
  [state frob]
  (call-api state "rtm.auth.getToken" {"frob" frob}))

;; If the token is valid, then it is returned, otherwise nil
(defn rtm-auth-checkToken
  "Checks if the token is still valid. Returns the token if it is valid, otherwise
returns nil"
  [state token]
  (call-api state "rtm.auth.checkToken" {"auth_token" token}))

;; Returns the lists for the user as a sequence in the following format:
;; ({:id "list_id" :name "list_name"} {:id "another_list" :name "another list name"} etc)
(defn rtm-lists-getList
  "Returns the lists for the user"
  [state]
  (call-api-with-token state "rtm.lists.getList"))

;; Returns all the tasks, or the tasks for a particular list. Supports the RTM
;; search filters. By default it uses status:incomplete to only return incomplete
;; tasks
;; NB: it only returns a sub-set of the data currently. I may add more in as I go along...
(defn rtm-tasks-getList
  "Gets all the tasks for a particular list, or all tasks if not list-id provided"
  ([state list-id]
     (rtm-tasks-getList state list-id "status:incomplete"))
  ([state list-id list-filter]
     (call-api-with-token state "rtm.tasks.getList" {"list_id" list-id, "filter" list-filter})))

;; Timeline is required for any write tasks
(defn rtm-timelines-create
  [state]
  (call-api-with-token state "rtm.timelines.create"))

;; Add a task
(defn rtm-tasks-add
  "Create and add a new task using smart add. The task is added to the inbox."
  [state name]
  (call-api-with-token state "rtm.tasks.add"
    {"timeline" (:timeline state), "parse" "1", "name" name}))

(defn- make-params-map
  [& args]
  (utils/debug (str "Make params map for: " args))
  (if (and (seq args) (even? (count args)))
    (apply assoc {} args)
    {}))

(defn- call-task-method  
  [method-name state list-id task-series-id task-id & args]
  (utils/debug (str "call-task-method: args = " args))
  (let [additional-params (apply make-params-map args)]
    (utils/debug (str method-name " params: list-id = " list-id ", additional" additional-params))
    (call-api-with-token state method-name
      (into  {"timeline" (:timeline state), "list_id" list-id, "taskseries_id" task-series-id, "task_id" task-id} additional-params))))

;; Delete a task
(defn rtm-tasks-delete
  [state list-id task-series-id task-id]
  (call-task-method "rtm.tasks.delete" state list-id task-series-id task-id))

(defn rtm-transactions-undo
  [state timeline transaction-id]
  (call-api-with-token state "rtm.transactions.undo"
    {"timeline" timeline, "transaction_id" transaction-id}))

;; mark as task as complete
(defn rtm-tasks-complete
  [state list-id task-series-id task-id]
  (call-task-method "rtm.tasks.complete" state list-id task-series-id task-id))

;; set the priority of a task
(defn rtm-tasks-setPriority
  "Note the order is slightly different here, with priority first"
  [priority state list-id task-series-id task-id]
  (call-task-method "rtm.tasks.setPriority" state list-id task-series-id task-id "priority" priority))

;; Create a new list
(defn rtm-lists-add
  [state name]
  (call-api-with-token state "rtm.lists.add"
    {"timeline" (:timeline state), "name" name}))

;; Move task to list
(defn rtm-tasks-moveTo
  [state from-list-id to-list-id task-series-id task-id]
  (call-api-with-token state "rtm.tasks.moveTo"
    {"timeline" (:timeline state), "from_list_id" from-list-id, "to_list_id" to-list-id,
     "taskseries_id" task-series-id, "task_id" task-id}))
