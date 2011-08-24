;; The entry point for the application. This contains the main entry for the
;; command line application. The idea is that it displays a prompt, you enter
;; a command, which is then executed. It's essentially a kind of REPL or shell.
(ns rtm-clj.core
  (:require [clojure.string :as str]
            [rtm-clj.api :as api]
            [swank.swank :as swank])
  (:gen-class :main true))

;; The map that contains all the commands. 
(def *commands* (atom {}))

;; And some aliases
(def *command-aliases* (atom {}))

;; This is the cache for the session
(def *cache* (atom {}))

;; utility functions
(defmulti as-int class)
(defmethod as-int Number [v]
  (int v))
(defmethod as-int String [s]
  (try
    (Integer/parseInt s)
    (catch Exception e nil)))
(defmethod as-int :default [x] nil)

;; # Commands
;; The entry point for putting the commands into the map. This associates a
;; Clojure function with a String, which is the command name. When a command
;; is executed, the correct function is looked up from here, and all the
;; arguments are passed to it.
(defn- duplicate-alias?
  [al cmd]
  (if-let [existing (@*command-aliases* al)]
    (not (= cmd existing))
    false))

(defn register-command-alias
  [al cmd]
  (if-not (duplicate-alias? al cmd)
    (swap! *command-aliases* assoc al cmd)))

(defn register-command
  "Registers the command for future use"
  [f name]
  (swap! *commands* assoc name f)
  (doseq [also (:also (meta f))]
    (register-command-alias also name))
  (if-let [cache-id (:cache-id (meta f))]
    (register-command-alias cache-id name)))

;; Now we have the section of the file which defines the commands. These
;; are just Clojure functions. However, due to a limitation in the current
;; implementation, the commands should only use explict parameters or & args,
;; otherwise the arity check will fail. 
;; Note that for the function to be picked up as a command, it must have the
;; :cmd metadata, defining the name
(defn ^{:cmd "exit" :also ["quit"]} exit
  "Exits the application"
  []
  (println "Good-bye")
  (System/exit 1))

(declare lookup-command)

(defn ^{:cmd "help" :also ["?", "h"]} help
  "Displays all the available commands, or provides help for a particular command"
  ([]
     (apply println (sort (keys @*commands*))))
  ([cmd]
     (if-let [f (lookup-command cmd)]
       (println (str cmd " "(:also (meta f)) ": " (:doc (meta f))))
       (println (str cmd ": command not found")))))

(defn ^{:cmd "swank" :also ["repl"]} start-swank
  "Start a swank server on the specified port. Defaults to 4005."
  ([]
     (swank/start-repl 4005))
  ([port]
     (swank/start-repl (as-int port))))

(defn ^{:cmd "echo" :also ["say"]} echo
  "Echos out the command: echo [text]"
  [& args]
  (apply println args))

(defn ^{:cmd "state"} state
  "Echos out the current state"
  []
  (println (api/get-state)))

;; This is the key part of the app. Display a set of values that the user
;; then selects by number
(defn- cache-put
  "Used to store data for the session"
  [key data]
  (swap! *cache* assoc key data))

(defn- cache-get
  [key]
  (@*cache* key))

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
      (cache-put cache-id id-map)
      (cache-put :last cache-id)
      (title heading)
      (doseq [item id-map]
        (println (str (key item) " - " (:name (val item)))))
      (divider)
      (println))
    (println "None")))

;; used twice, so factored out
(defn- indexify
  "Creates a map using the supplied collection, where each key is a number starting at zero"
  [c]
  (apply array-map (interleave (iterate inc 1) c)))

(defn- create-id-map
  "Creates the map that is used to output the lists, tasks etc"
  [items]
  (for [item items :let [id-map {:id (:id item), :name (:name item), :data item}]]
    id-map))

;; Not only displays the lists, but also stores them away for reference, so user can do
;; list 0
;; to display all the tasks in list 0
(defn ^{:cmd "list", :also ["ls" "l"], :cache-id :lists} display-lists
  "Displays all the lists or all the tasks for the selected list"
  ([]
     (if-let [lists (api/rtm-lists-getList)]
       (display-and-cache
        (indexify (create-id-map lists))
        :lists
        "Lists")))
  ([i]
     (let [idx (as-int i)]
       (if-let [cached-lists (cache-get :lists)]
         (if-let [the-list (cached-lists idx)]
           (if-let [tasks (flatten (api/rtm-tasks-getList (:id the-list)))]
             (display-and-cache
              (indexify (create-id-map tasks))
              :tasks
              (str "List: " (:name the-list)))))))))

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

;; Command for viewing a particular task
(defn ^{:cmd "task", :also ["t"], :cache-id :tasks} view-task
  "Displays the details of a particular task from the last displayed list."
  [i]
  (if-let [task ((cache-get :tasks) (as-int i))]
    (let [task-data (:data task)]
      (cache-put :last-task task-data)
      (display-task task-data))))

;; Command for adding a task
(defn ^{:cmd "new", :also ["add"]} add-task
  "Creates a new task using smart-add"
  [& args]
  (if-let [new-task (api/rtm-tasks-add (str/join " " args))]
    (display-task (first (flatten new-task)))
    (println "Failed to add task")))

;; # Dispatching Commands
;; This section of the code is the part that parses the input from the user, and
;; works out which command to execute.
(defn- lookup-alias
  [cmd]
  (if (@*command-aliases* cmd)
    (@*command-aliases* cmd)
    cmd))

(defn- command-exists?
  [cmd]
  (if (@*commands* cmd) true false))

;; Supports looking at the menu and typing a number for the
;; displayed menu
(defn- lookup-index-command [i]
  "Used to directly execute an item from the displayed index."
  (if-let [last-id (cache-get :last)]
    (if-let [f (lookup-command last-id)]
      ;; construct a function that takes no args, and pass it the index
      (with-meta
        (fn [] (f i)) {:arglists '([])}))))

(defn lookup-command 
  "Looks up the command by name, also checking for aliases. Returns the function."
  [cmd]
  (let [cmd-name (lookup-alias cmd)]
    (if (command-exists? cmd-name)
      (@*commands* cmd-name)
      (if-let [i (as-int cmd)]
          (lookup-index-command i)))))

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


;; I love this bit of code. There's probably an easier way to do it, but then
;; I didn't have much experience of Clojure when I wrote it. This takes a single
;; arglist for a function (i.e. the bit in square brackets, as defined in defn),
;; and returns a function which takes one argument (a number) and returns true
;; if that number is compatible with the arity of the arg list.
;; For example:
;;
;; * [] - the only valid number is 0
;; * [x] - the only valid number is 1
;; * [& args] - any number >= 0
;; * [x & args] - any number >= 1
;;
;; This is only a simple check. It doesn't handle arglists that have a destructuring
;; form, hence the restriction noted above in the commands section, about using
;; explicit args or & args.
(defn arity-check
  "Returns a function that evaluates to true if the arity matches the count"
  [arglist]
  ;; special case - if arglist is of zero length then no need to check for & args
  (if (= 0 (count arglist))
    #(= % 0)
    (let [arg-map (apply assoc {}
                         (interleave arglist (range 0 (count arglist))))]
      ;; if & args found then number of args is >= the position of the &
      ;; otherwise it's just a simple size comparison
      (if ('& arg-map)
        #(>= % ('& arg-map))
        #(= % (count arglist))))))

;; This takes a function, f, and the args that it is proposed will be passed
;; to the function. The arity of the args must match the arity of at least one
;; of the function's arglists. Hence this calls arity-check for each of the
;; arglists, then calls each of the functions returned with the number of
;; arguments. The result is a set of booleans containing the result of the
;; function calls.
(defn arity-matches-args
  "Returns true if the args match up to the function"
  [f args]
  (let [arity-check-fns (map arity-check (:arglists (meta f)))]
    ;; Check that at least one of the arglists can accept all the args.
    ((set (map #(% (count args)) arity-check-fns)) true)))

;; Looks up the registered commands to find a function that is mapped to the
;; command the user entered. If it is found, and the arity matches, then
;; the function is called, passing the rest of the args.
(defn- call-cmd
  "Destructures the command entered by the user, looking up the function that
implements the command using the first element. If found, the function is called
with the rest of the args"
  [cmd & args]
  (if-let [f (lookup-command cmd)]
    (if (arity-matches-args f args)
      (try
        (apply f args)
        (catch Exception e (println (str "Exception: " (.getMessage e)) )))
      (do
        (println (str cmd ": wrong number of args"))
        (help cmd)))
    (println (str cmd ": command not found"))))

;; This works with the full command string that the user entered
;; e.g. help echo
;; Splits the command into a sequence of words, and calls call-cmd
;; to execute the function.
(defn call
  "Pass the raw command string in here as read from the prompt. Parses it and
delegate to the call-cmd"
  [cmd-str]
  (apply call-cmd (str/split cmd-str #" ")))

;; This is the repl. Just repeatedly prompts for input and executes the commmand.
(defn cmd-loop
  "This is repl, if you like, for rtm. Read a command, evaluate it, print the result, loop"
  []
  (call (prompt!))
  (recur))

;; Dynamically discover commands
(defn- discover-commands
  []
  (doseq [f (vals (ns-publics 'rtm-clj.core)) :when (:cmd (meta f)) :let [name (:cmd (meta f))]]
    (register-command f name)))

;; Checks to see if we have a valid token. If not then launches the browser for authorization.
;; If a valid token is returned, returns true, otherwise if login failed, returns false
(defn- login
  "This is a helper method that pulls the whole auth process together"
  ([]
     (login true))
  ([load-state]
     (if load-state (api/load-state!)) ;; only load the state if it was requested
     (if-not (api/have-valid-token?)
       (if-let [frob (api/request-authorization)]
         (do
           (prompt! "Authorise application in broswer and <RETURN> to continue..."
                    (fn [x] nil))
           (if-let [new-token (api/rtm-auth-getToken frob)]
             (if-let [valid-token (api/rtm-auth-checkToken new-token)]
               (do                 
                 (api/set-token! valid-token)
                 (api/save-state)
                 true)
               false))))
       true)))

;; # Main Control Loop
;; The main method, the entry point for running from Java.
;; It tries to load a previous state from the file in the home directory, to
;; retrieve the api key and shared secret, which are needed to interact with
;; the Remember the Milk API. Once it is all set up, it just calls the cmd-loop.
(defn -main [& args]
  (discover-commands)
  (if-not (api/load-state!)
    (let [api-key (prompt! "Enter api key: ")
          secret (prompt! "Enter shared secret: ")]
      (api/set-api-key! api-key)
      (api/set-shared-secret! secret)
      (api/save-state)))
  (if (login)
    (cmd-loop)
    (do
      (println "Login failed")
      (exit))))
