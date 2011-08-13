(ns rtm-clj.core
  (:require [clojure.string :as str])
  (:gen-class :main true))


(def *commands* (atom {}))


(defn register-command
  "Registers the command for future use"
  [f name]
  (swap! *commands* assoc name f))

;;------------------------------------------------------------

;; only use explict parameters or & args, otherwise the
;; arity check will fail

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


(defn echo
  "Echos out the command: echo [text]"
  [& args]
  (apply println args))


(register-command help "help")
(register-command exit "exit")
(register-command echo "echo")

;;------------------------------------------------------------

(defn lookup-command 
  "Parses the command string returning a function to be executed"
  [cmd & args]
  (@*commands* cmd))

(defn prompt!
  "Displays the prompt for the user and reads input for stdin. Returns a vector
of the individual words that were entered."
  ([]
     (prompt! "rtm> "))
  ([s]
     (print s)
     (flush)
     (read-line)))


;; higher order functions rock!
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


;; this builds a collection of functions, one for each of the arglists
;; which evaluates to true if the number of args matches the arity of the
;; arglist. it then applies each function in turn against the size of the
;; args, and determines if any of them returned true. if at least one of
;; them returned true, then we can safely do the call
(defn arity-matches-args
  "Returns true if the args match up to the function"
  [f args]
  (let [arity-check-fns (map arity-check (:arglists (meta f)))]
    ((set (map #(% (count args)) arity-check-fns)) true)))


(defn- call-cmd
  "Destructures the command entered by the user, looking up the function that
implements the command using the first element. If found, the function is called
with the rest of the args"
  [cmd & args]
  (if-let [f (lookup-command cmd)]
    (if (arity-matches-args f args)
      (apply f args)
      (do
        (println (str cmd ": wrong number of args"))
        (help cmd)))
    (println (str cmd ": command not found"))))


(defn call
  "Pass the raw command string in here as read from the prompt. Parses it and
delegate to the call-cmd"
  [cmd-str]
  (apply call-cmd (str/split cmd-str #" ")))


(defn cmd-loop
  "This is repl, if you like, for rtm. Read a command, evaluate it, print the result, loop"
  []
  (call (prompt!))
  (recur))


(defn -main [& args]
  (cmd-loop))
