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

;; # Commands
;; The entry point for putting the commands into the map. This associates a
;; Clojure function with a String, which is the command name. When a command
;; is executed, the correct function is looked up from here, and all the
;; arguments are passed to it.
(defn register-command
  "Registers the command for future use"
  [f name]
  (swap! *commands* assoc name f))

;; Now we have the section of the file which defines the commands. These
;; are just Clojure functions. However, due to a limitation in the current
;; implementation, the commands should only use explict parameters or & args,
;; otherwise the arity check will fail. 

(defn exit
  "Exits the application"
  []
  (println "Good-bye")
  (System/exit 1))

(declare lookup-command)

(defn help
  "Displays all the available commands, or provides help for a particular command"
  ([]
     (apply println (sort (keys @*commands*))))
  ([cmd]
     (if-let [f (lookup-command cmd)]
       (println (str cmd ": " (:doc (meta f))))
       (println (str cmd ": command not found")))))

(defn start-swank
  "Start a swank server on the specified port. Defaults to 4005."
  ([]
     (start-swank 4005))
  ([port]
     (swank/start-repl (Integer/parseInt port))))

(defn echo
  "Echos out the command: echo [text]"
  [& args]
  (apply println args))

(defn state
  "Echos out the current state"
  []
  (println (api/get-state)))

(defn display-lists
  "Displays all the lists"
  []
  (if-let [lists (api/rtm-lists-getList)]
    (println lists)))

;; At some point I think I will replace these separate defn and register-command
;; calls with a macro that combines them all.
(register-command help "help")
(register-command exit "exit")
(register-command echo "echo")
(register-command state "state")
(register-command display-lists "list")
(register-command start-swank "swank")

;; # Dispatching Commands
;; This section of the code is the part that parses the input from the user, and
;; works out which command to execute.
(defn lookup-command 
  "Parses the command string returning a function to be executed"
  [cmd & args]
  (@*commands* cmd))

;; This function displays the prompt, reads input, and returns the full line
;; as a String. Note that it is parameterized so that it can be used to request
;; specific input from the user.
;; It would probably be useful to add a validation function in here as well to
;; make it more general.
(defn prompt!
  "Displays the prompt for the user and reads input for stdin. Returns a vector
of the individual words that were entered."
  ([]
     (prompt! "rtm> "))
  ([s]
     (print s)
     (flush)
     (let [line (read-line)]
       (if (str/blank? line)
         (recur s)
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

;; # Main Control Loop
;; The main method, the entry point for running from Java.
;; It tries to load a previous state from the file in the home directory, to
;; retrieve the api key and shared secret, which are needed to interact with
;; the Remember the Milk API. Once it is all set up, it just calls the cmd-loop.
(defn -main [& args]
  (if-not (api/load-state!)
    (let [api-key (prompt! "Enter api key: ")
          secret (prompt! "Enter shared secret: ")]
      (api/set-api-key! api-key)
      (api/set-shared-secret! secret)
      (api/save-state)))
  (if (api/login)
    (cmd-loop)
    (do
      (println "Login failed")
      (exit))))
