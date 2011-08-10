(ns rtm-clj.core
  (:require [clojure.string :as string])
  (:gen-class :main true))


(def *commands* (atom {}))

(defn- register-command
  "Registers the command for future use"
  [f name]
  (swap! *commands* assoc name f))

;;------------------------------------------------------------

(defn exit []
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

(defn prompt! []
  (print "rtm> ")
  (flush)
  (str (read-line)))

(defn call [cmd args]
  (apply cmd []))

(defn cmd-loop []
  (let [[cmd & args] (string/split (prompt!) #" ")]
    (if-let [f (lookup-command cmd)]
      (call f args)
      (println (str cmd ": command not found")))
    (recur)))

(defn -main [& args]
  (cmd-loop))
