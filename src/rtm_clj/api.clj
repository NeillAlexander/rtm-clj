(ns rtm-clj.api
  (:require [clj-http.client :as http]))

;; constant
(def *base-url* "http://api.rememberthemilk.com/services/rest/?")

;; state
(def *api-key* (atom ""))
(def *shared-secret* (atom ""))


(defn set-api-key!
  "Sets the key for the session"
  [key]
  (reset! *api-key* key))


(defn set-shared-secret!
  "Sets the shared secret for the session"
  [secret]
  (reset! *shared-secret* secret))


(defn build-rtm-url
  "Builds the url to hit the rest service"
  [method]
  (str *base-url* "method=" method "&api_key=" @*api-key*))


;; see http://www.rememberthemilk.com/services/api/request.rest.rtm
(defn echo
  "Calls the rtm.test.echo method with the specified params"
  [params]
  (let [url (str (build-rtm-url "rtm.test.echo") "&" params)]
    (http/get url)))
