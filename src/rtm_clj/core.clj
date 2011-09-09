;; The entry point for the application. This contains the main entry for the
;; command line application. The idea is that it displays a prompt, you enter
;; a command, which is then executed. It's essentially a kind of REPL or shell.
(ns rtm-clj.core
  (:require [rtm-clj.command :as cmd]
            [rtm-clj.state :as state]
            [rtm-clj.utils :as utils]
            [clojure.string :as str])
  (:gen-class :main true))

;; The map that contains all the commands. 
(def *commands* (atom {}))

;; And some aliases
(def *command-aliases* (atom {}))

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


(declare lookup-command)

;; Ideally this would go into the command namespace, but it relies on looking
;; up other commands so really, it needs to live at the top level
(defn ^{:cmd "help" :also ["?", "h"]} help
  "Displays all the available commands, or provides help for a particular command"
  ([state]
     (apply println (sort (keys @*commands*))))
  ([state cmd]
     (if-let [f (lookup-command state cmd)]
       (println (str cmd " "(:also (meta f)) ": " (:doc (meta f))))
       (println (str cmd ": command not found")))))

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
(defn- lookup-index-command [state i]
  "Used to directly execute an item from the displayed index."
  (if-let [last-id (state/cache-get :last)]
    (if-let [f (lookup-command state last-id)]
      ;; construct a function that takes state as the  arg, and pass it the index
      (with-meta
        (fn [f-state] (f f-state i)) {:arglists '([s])}))))

(defn lookup-command 
  "Looks up the command by name, also checking for aliases. Returns the function."
  [state cmd]
  (let [cmd-name (lookup-alias cmd)]
    (if (command-exists? cmd-name)
      (@*commands* cmd-name)
      (if-let [i (utils/as-int cmd)]
          (lookup-index-command state i)))))

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
  [f num-args]
  (let [arity-check-fns (map arity-check (:arglists (meta f)))]
    ;; Check that at least one of the arglists can accept all the args.
    ((set (map #(% num-args) arity-check-fns)) true)))

;; Looks up the registered commands to find a function that is mapped to the
;; command the user entered. If it is found, and the arity matches, then
;; the function is called, passing the rest of the args.
(defn- call-cmd
  "Destructures the command entered by the user, looking up the function that
implements the command using the first element. If found, the function is called
with the rest of the args"
  [state cmd & args]
  (if-let [f (lookup-command state cmd)]
    ;; arity match needs to include the state arg
    (if (arity-matches-args f (inc (count args)))
      (try
        (apply f state args)
        (catch Exception e
          (println (str "Exception: " (.toString e)))
          (.printStackTrace e)))
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
  [state cmd-str]
  (apply call-cmd state (str/split cmd-str #" ")))

;; This is the repl. Just repeatedly prompts for input and executes the commmand.
(defn cmd-loop
  "This is repl, if you like, for rtm. Read a command, evaluate it, print the result, loop"
  [state]
  (call state (utils/prompt!))
  (recur (state/load-state)))

;; Dynamically discover commands
(defn- discover-commands
  ([]
     (discover-commands 'rtm-clj.command)
     (discover-commands 'rtm-clj.core))
  ([namespace]
     (doseq [f (vals (ns-publics namespace)) :when (:cmd (meta f)) :let [name (:cmd (meta f))]]
       (register-command f name))))

;; # Main Control Loop
;; The main method, the entry point for running from Java.
;; It tries to load a previous state from the file in the home directory, to
;; retrieve the api key and shared secret, which are needed to interact with
;; the Remember the Milk API. Once it is all set up, it just calls the cmd-loop.
(defn -main [& args]
  (discover-commands)
  (if-let [state (cmd/login (cmd/init-state))]
    (cmd-loop (cmd/init-timeline state))
    (do
      (println "Login failed")
      (cmd/exit))))
