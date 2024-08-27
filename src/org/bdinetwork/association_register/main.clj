(ns org.bdinetwork.association-register.main
  (:require [org.bdinetwork.association-register.system :as system]
            [org.bdinetwork.association-register.data-source :as ds]
            [buddy.core.keys :as keys]
            [nl.jomco.resources :refer [close defresource]]
            [nl.jomco.envopts :as envopts]
            [environ.core :refer [env]])
  (:gen-class))

(defresource system)

(def opt-specs
  {:private-key              ["Server private key pem file" :private-key]
   :public-key               ["Server public key pem file" :public-key]
   :x5c                      ["Server certificate chain pem file" :x5c]
   :data-source              ["YAML file specifying parties and trusted list" :data-source]
   :server-id                ["Server ID (EORI)" :str]
   :hostname                 ["Server hostname" :str :default "localhost"]
   :port                     ["Server HTTP Port" :int :default 8080]
   :access-token-ttl-seconds ["Access token time to live in seconds" :int :default 600]})

(defmethod envopts/parse :private-key
  [s _]
  [(keys/private-key s)])

(defmethod envopts/parse :public-key
  [s _]
  [(keys/public-key s)])

(defmethod envopts/parse :x5c
  [s _]
  [(system/x5c s)])

(defmethod envopts/parse :data-source
  [s _]
  [(ds/yaml-in-memory-data-source-factory s)])

(defn -main [& args]
  (let [[config errs] (envopts/opts env opt-specs)]
    (when errs
      (.println *err* "Error in environment configuration")
      (.println *err* (envopts/errs-description errs))
      (.println *err* "Available environment vars:")
      (.println *err* (envopts/specs-description opt-specs))
      (System/exit 1))
    (defresource system (system/run-system config))))
