;; This layer sits on top of the api layer and provides the higher level commands
;; for the app.
(ns rtm-clj.command
  (:require [rtm-clj.api :as api]
            [rtm-clj.state :as state]
            [rtm-clj.utils :as utils]
            [rtm-clj.xml :as xml]
            [clojure.string :as str]
            [swank.swank :as swank])
  (:import [java.net URI]))

(defn- create-id-map
  "Creates the map that is used to output the lists, tasks etc"
  [items]
  (for [item items :let [id-map {:id (:id item), :name (:name item), :data item}]]
    id-map))

(defn- divider
  []
  (println "============================================================"))

(defn- title
  [t]
  (println)
  (divider)
  (println t)
  (divider))

;; Display the results. The contract is that the map must be in the following format:
;; {0, {:id 123 :name "Inbox"}, 1 {:id 1234 :name "Sent"}}
;; The map is cached using the provided key for future lookup.
;; That is the mechanism for storing data in the session.
(defn- display-and-cache
  "Requires map in format: id, {:id id :name name}"
  [id-map cache-id heading]
  (if (not (zero? (count id-map)))
    (do
      (state/cache-put cache-id id-map)
      (state/cache-put :last cache-id)
      (title heading)
      (doseq [item id-map]
        (println (str (key item) " - " (:name (val item)))))
      (divider)
      (println))
    (println "None")))

(defn- display-task
  [task-data]
  (title (str "Task: " (:name task-data)))
  (println (str "Created: " (:created task-data)))
  (println (str "Due: " (:due task-data)))
  (println (str "URL: " (:url task-data)))
  (doseq [note (flatten (:notes task-data))]
    (println (str "Note: " (:title note))))
  (divider)
  (println))

(defn get-lists
  "Calls the rtm api to retrieve lists, returning the attributes from the xml"
  [state]
  (if-let [list-xml (xml/to-xml (api/rtm-lists-getList state))]
    (for [x (xml-seq list-xml) :when (= :list (:tag x))]
      (:attrs x))))

;; Generates the url that the user needs to access in order to grant
;; access for the client to access their account, and launches the
;; browser. Returns the frob.
(defn request-authorization
  "See http://www.rememberthemilk.com/services/api/authentication.rtm"
  [state frob]
  (if frob
    (do
      (if-let [url (api/build-rtm-url state {"perms" "delete", "frob" frob} api/*auth-url-base*)]
        (.browse (java.awt.Desktop/getDesktop) (URI. url)))
      frob)))

(defn get-frob
  "Calls rtm-auth-getFrob and parses the response to extract the frob value."
  [state]
  (first (xml/parse-response (api/rtm-auth-getFrob state) :frob)))

(defn get-token
  "Calls the api to get the token"
  [state frob]
  (first (xml/parse-response (api/rtm-auth-getToken state frob) :token)))

(defn validate-token
  "Calls the api to check the validity of the token"
  ([state]
     (if (:token state)
       (validate-token state (:token state))
       nil))
  ([state token]
     (first (xml/parse-response (api/rtm-auth-checkToken state token) :token))))

;; Checks to see if we have a valid token. If not then launches the browser for authorization.
;; Returns the state.
(defn login
  "This is a helper method that pulls the whole auth process together"
  ([state]
     (if-not (validate-token state)
       (if-let [frob (request-authorization state (get-frob state))]
         (do
           (utils/prompt! "Authorise application in browser and <RETURN> to continue..."
                    (fn [x] nil))
           (if-let [new-token (get-token state frob)]
             (if-let [valid-token (validate-token state new-token)]
               (state/save-state! (state/set-token state valid-token))))))
       state)))

(defn init-api-key
  [state]
  (if-not (api/api-key-set? state)
    (state/set-api-key state (utils/prompt! "Enter api key: "))
    state))

(defn init-shared-secret
  [state]
  (if-not (api/shared-secret-set? state)
    (state/set-shared-secret state (utils/prompt! "Enter shared secret: "))
    state))

(defn init-timeline
  [state]
  (if-let [timeline (first (xml/parse-response (api/rtm-timelines-create state) :timeline))]
    (assoc state :timeline timeline)))

(defn init-state
  "Sets up the state for the session"
  ([]
     (init-state (state/load-state)))
  ([state]
     (->> state
          init-api-key
          init-shared-secret          
          state/save-state!)))

;; Now we have the section of the file which defines the commands. These
;; are just Clojure functions. However, due to a limitation in the current
;; implementation, the commands should only use explict parameters or & args,
;; otherwise the arity check will fail. 
;; Note that for the function to be picked up as a command, it must have the
;; :cmd metadata, defining the name
(defn ^{:cmd "exit" :also ["quit"]} exit
  "Exits the application"
  [state]
  (println "Good-bye")
  (System/exit 1))

;; Start a repl to connect to from Slime
(defn ^{:cmd "swank" :also ["repl"]} start-swank
  "Start a swank server on the specified port. Defaults to 4005."
  ([state]
     (swank/start-repl 4005))
  ([state port]
     (swank/start-repl (utils/as-int port))))

(defn ^{:cmd "echo" :also ["say"]} echo
  "Echos out the command: echo [text]"
  [state & args]
  (apply println args))

(defn ^{:cmd "state"} state
  "Echos out the current state"
  [state]
  (println state))

;; Not only displays the lists, but also stores them away for reference, so user can do
;; list 0
;; to display all the tasks in list 0
(defn ^{:cmd "list", :also ["ls" "l"], :cache-id :lists} display-lists
  "Displays all the lists or all the tasks for the selected list"
  ([state]
     (if-let [lists (get-lists state)]
       (display-and-cache
        (utils/indexify (create-id-map lists))
        :lists
        "Lists")))
  ([state i]
     (let [idx (utils/as-int i)]
       (if-let [cached-lists (state/cache-get :lists)]
         (if-let [the-list (cached-lists idx)]
           (if-let [tasks (flatten (xml/parse-task-series-response (xml/to-xml (api/rtm-tasks-getList state (:id the-list)))))]
             (display-and-cache
              (utils/indexify (create-id-map tasks))
              :tasks
              (str "List: " (:name the-list)))))))))

;; Command for viewing a particular task
(defn ^{:cmd "task", :also ["t"], :cache-id :tasks} view-task
  "Displays the details of a particular task from the last displayed list."
  [state i]
  (if-let [task ((state/cache-get :tasks) (utils/as-int i))]
    (let [task-data (:data task)]
      (state/cache-put :last-task task-data)
      (display-task task-data))))

;; Command for adding a task
(defn ^{:cmd "new", :also ["add"]} add-task
  "Creates a new task using smart-add"
  [state & args]
  (if-let [new-task (xml/parse-task-series-response (xml/to-xml (api/rtm-tasks-add state (str/join " " args))))]
    (display-task (first (flatten new-task)))
    (println "Failed to add task")))

(defn ^{:cmd "rm", :also ["delete"]} delete-task
  [state tasknum]
  (if-let [task ((state/cache-get :tasks) (utils/as-int tasknum))]
    task))
