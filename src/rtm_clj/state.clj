;; Contains the mutable stuff, and also the non-mutable state
;; (if that makes sense)
(ns rtm-clj.state
  (:require [rtm-clj.utils :as utils]))

;; At some point I may make this configurable. This is where the
;; state is stored.
(def *state-file* (str (System/getenv "HOME") "/.rtm-clj"))

;; This stores all the undoable tasks
(def *undoables* (atom ()))

;; This is the cache for the session
(def *cache* (atom {}))

(defn cache-put
  "Used to store data for the session"
  [key data]
  (utils/debug (str "Storing " key " --> " data))
  (swap! *cache* assoc key data))

(defn cache-get
  [key]
  (@*cache* key))

;; is now stored in a map which is passed around rather than having a global
(defn new-state
  "Creates a new state map"
  []
  {:api-key nil :shared-secret nil :token nil :timeline nil})

(defn set-token
  [state token]
  (assoc state :token token))

(defn set-api-key
  "Sets the key for the session"
  [state key]
  (assoc state :api-key key))

(defn set-shared-secret
  "Sets the shared secret for the session"
  [state secret]
  (assoc state :shared-secret secret))

;; ## Persistence
;; Store the state to a file, using the default location.
(defn save-state!
  "Save the api key and shared secret to a file"
  ([state]
     (save-state! *state-file* state))
  ([f state]
     (spit f state)
     state))

;; Load the state up again. This is called on startup. Note
;; the exclamation mark, due to the fact that it overrides
; the current state.
(defn load-state
  "Tries to set up the api key and shared secret from a file"
  ([]
     (load-state *state-file*))
  ([f]
     (try
       (read-string (slurp f))       
       (catch Exception e
         (new-state)))))

;; stores it away for future reference
(defn store-undoable
  [m]
  (swap! *undoables* conj m))

(defn remove-undoable
  [idx]
  (utils/debug (str "Removing undoable: " idx))
  (swap! *undoables* #(apply list (vals (dissoc  (utils/indexify %) idx)))))

(defn undoables [] @*undoables*)

(defn get-list-sort-order
  "Retrieves the sort order for the list from the state or nil if none"
  [state list-id]
  (if-let [sort-order-map (:sort-order state)]
    (sort-order-map list-id)
    nil))
