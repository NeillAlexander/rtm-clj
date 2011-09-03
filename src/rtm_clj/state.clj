;; Contains the mutable stuff, and also the non-mutable state
;; (if that makes sense)
(ns rtm-clj.state)

;; At some point I may make this configurable. This is where the
;; state is stored.
(def *state-file* (str (System/getenv "HOME") "/.rtm-clj"))

;; This is the cache for the session
(def *cache* (atom {}))

(defn cache-put
  "Used to store data for the session"
  [key data]
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