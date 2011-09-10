;; This layer sits on top of the api layer and provides the higher level commands
;; for the app.
(ns rtm-clj.command
  (:require [rtm-clj.api :as api]
            [rtm-clj.state :as state]
            [rtm-clj.utils :as utils]
            [rtm-clj.xml :as xml]
            [clojure.string :as str]
            [swank.swank :as swank]
            [clansi :as clansi])
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

(def priority-formats
  {"1" :red, "2" :blue, "3" :cyan})

(defn- colorize
  [str priority]
  (if-let [color-key (priority-formats priority)]
    (clansi/style str color-key :bright)
    str))

(defn- format-task
  "Formats the task for display"
  [name-key item-map]
  (let [item (val item-map)
        priority (:priority (:data item))]    
    (colorize (str (format "%5s" (key item-map)) " - " (name-key item)) priority)))

;; Display the results. The contract is that the map must be in the following format:
;; {0, {:id 123 :name "Inbox"}, 1 {:id 1234 :name "Sent"}}
(defn- display-id-map
  "Requires map in format: id, {:id id :name name}"
  ([heading id-map]
     (display-id-map heading :name id-map))
  ([heading name-key id-map]
     (if (not (zero? (count id-map)))
       (do
         (title heading)
         (doseq [item id-map]
           (println (format-task name-key item)))
         (divider)
         (println)))
     id-map))

;; The map is cached using the provided key for future lookup.
;; That is the mechanism for storing data in the session.
(defn- cache-id-map
  "Requires map in format: id, {:id id :name name}"
  [cache-id id-map]
  (state/cache-put cache-id id-map)
  (state/cache-put :last cache-id)
  id-map)

(defn- display-task
  [task-data]
  (utils/debug (str "Task data:" task-data))
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
      (utils/debug (str "Requesting auth with frob '" frob "'"))
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
  (state/save-state!  state)
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

(defn- apply-sort-order
  [state list-id the-list]
  (if-let [sort-keys (state/get-list-sort-order state list-id)]
    (sort (apply utils/make-map-comparator + sort-keys) the-list)
    the-list))

;; Not only displays the lists, but also stores them away for reference, so user can do
;; list 0
;; to display all the tasks in list 0
(defn ^{:cmd "list", :also ["ls" "l"], :cache-id :lists} display-lists
  "Displays all the lists or all the tasks for the selected list"
  ([state]
     (if-let [lists (get-lists state)]
       (->> (utils/indexify (create-id-map lists))
            (display-id-map "Lists")
            (cache-id-map :lists))))
  ([state i]
     (let [idx (utils/as-int i)]
       (if-let [cached-lists (state/cache-get :lists)]
         (if-let [the-list (cached-lists idx)]
           (if-let [tasks (xml/parse-task-series-response (api/rtm-tasks-getList state (:id the-list)))]
             (do
               (state/cache-put :last-list-num idx)
               (->> (apply-sort-order state (:id the-list) tasks)
                    (create-id-map)
                    (utils/indexify)
                    (display-id-map (str "List: " (:name the-list)))
                    (cache-id-map :tasks)))))))))

(defn- display-last-list
  [state]
  (if-let [last-list-num (state/cache-get :last-list-num)]
    (display-lists state last-list-num)))

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
  (if-let [new-task (xml/parse-add-task-response (api/rtm-tasks-add state (str/join " " args)))]
    (do
      (utils/debug (str "new-task: " new-task))
      (display-task (first new-task)))
    (println "Failed to add task")))

;; Command to enable debug
(defn ^{:cmd "debug"} switch-debug-on!
  [state]
  (utils/switch-debug-on! true))

(defn ^{:cmd "nodebug"} switch-debug-off!
  [state]
  (utils/switch-debug-on! false))

(defn- cache-if-undoable
  "Takes the raw xml response, and caches the details if it is undoable. The msg is what is displayed in the history."
  [state msg xml-response]
  (if-let [undoable (xml/parse-undoable xml-response (:timeline state))]
    (state/store-undoable (assoc undoable :message msg)))
  xml-response)

;; Helper function which abstracts out some common functionality when acting on a list
;; of tasks (e.g. rm, mark as complete etc)
(defn- task-command
  [f undo-msg state tasknum & others]
  (utils/debug (str "task-command: tasknum=" tasknum ", others=" others))
  (if-let [task (:data ((state/cache-get :tasks) (utils/as-int tasknum)))]
    (do
      (utils/debug (str "task: " task))
      (utils/debug (cache-if-undoable state (str undo-msg " task \"" (:name task) "\"")
                                      (f state (:list-id task) (:task-series-id task) (:id task))))
      (if (seq others)
        (recur f undo-msg state (first others) (rest others))
        (display-last-list state)))))

(defn ^{:cmd "rm", :also ["delete"]} delete-task
  "Delete one or more tasks (by index)."
  [state tasknum & others]
  (apply task-command api/rtm-tasks-delete "Deleted" state tasknum others))

(defn ^{:cmd "complete", :also ["c"]} complete-task
  "Mark one or more tasks as complete (by index)."
  [state tasknum & others]
  (apply task-command api/rtm-tasks-complete "Completed" state tasknum others))

;; These are ripe for converting to macros
(defn- set-priority
  [priority state tasknum & others]
  (apply task-command (partial api/rtm-tasks-setPriority priority) (str "Set priority" priority) state tasknum others))

(defn ^{:cmd "p0"} set-priority-0
  "Sets the priority of a task to 0"
  [state tasknum & others]
  (apply set-priority "0" state tasknum others))

(defn ^{:cmd "p1"} set-priority-1
  "Sets the priority of a task to 1"
  [state tasknum & others]
  (apply set-priority "1" state tasknum others))

(defn ^{:cmd "p2"} set-priority-2
  "Sets the priority of a task to 2"
  [state tasknum & others]
  (apply set-priority "2" state tasknum others))

(defn ^{:cmd "p3"} set-priority-3
  "Sets the priority of a task to 3"
  [state tasknum & others]
  (apply set-priority "3" state tasknum others))

(defn ^{:cmd "undo"} undo
  "Displays undoable actions and allows to undo"
  ([state]
     (->> (utils/indexify (state/undoables))
                  (display-id-map "Undoable Tasks" :message)
                  (cache-id-map :undos)))
  ([state idx]
     (if-let [undo-map (state/cache-get :undos)]
       (if-let [um (undo-map (utils/as-int idx))]
         (if (api/rtm-transactions-undo state (:timeline um) (:transaction-id um))
           (state/remove-undoable (utils/as-int idx)))
         (println "Error: Nothing found to undo")))))

(defn ^{:cmd "newlist", :also ["nl"]} create-new-list
  "Creates a new list"
  [state & name]
  (api/rtm-lists-add state (str/join " " name)))

(defn- get-list
  "Gets the list from the cache"
  [id]
  ((state/cache-get :lists) (utils/as-int id)))

(defn ^{:cmd "move", :also ["mv"]} move-task
  "Move tasks by id to a list, which will be prompted for."
  [state task-idx & more]
  (display-lists state)
  (if-let [to-list (get-list (utils/prompt! "Move task(s) to which list? " (complement utils/as-int)))]
    (loop [tasknum task-idx
           others more]        
      (if-let [task (:data ((state/cache-get :tasks) (utils/as-int tasknum)))]
        (do          
          (utils/debug (cache-if-undoable state (str "Moved task \"" (:name task) "\" to " (:name to-list))
                                          (api/rtm-tasks-moveTo state (:list-id task) (:id to-list) (:task-series-id task) (:id task))))
          (if (seq others)
            (recur (first others) (rest others))))))))

(defn- set-sort-order
  [state list-num the-list & keys]
  (let [state (assoc-in state [:sort-order (:id the-list)] keys)]
    (state/cache-put :state state)
    (state/save-state! state)
    (display-lists state list-num)))

(defn ^{:cmd "sort", :also ["s"]} sort-list
  "sort [listnum] [d|p]"
  [state list-num by]
  (if-let [the-list (get-list list-num)]
    (cond
     (= "d" by) (set-sort-order state list-num the-list :due)
     (= "p" by) (set-sort-order state list-num the-list :priority)
     (= "n" by) (set-sort-order state list-num the-list :name)
     :else (println "Unknown sort order: d = by due date, n = name, p = by priority"))))
