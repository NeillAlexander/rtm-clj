(ns rtm-clj.core
  (:require [clojure.string :as str])
  (:gen-class :main true))


(def *commands* (atom {}))

(defn register-command
  "Registers the command for future use"
  [f name]
  (swap! *commands* assoc name f))

;;------------------------------------------------------------

(defn exit
  "Exits the application"
  []
  (println "Good-bye")
  (System/exit 1))

(defn help
  "Displays all the available commands"
  []
  (println (keys @*commands*)))

(register-command help "help")
(register-command exit "exit")

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

(defn- call-cmd
  "Destructures the command entered by the user, looking up the function that
implements the command using the first element. If found, the function is called
with the rest of the args"
  [cmd & args]
  (if-let [f (lookup-command cmd)]
    (apply f [])
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
