(ns user
  (:require [org.bdinetwork.authorization-register.main :as main]
            [org.bdinetwork.authorization-register.system :as system]
            [nl.jomco.resources :refer [defresource close]]))

(def config (main/config))

(defresource system)

(defn start!
  []
  (defresource system
    (system/run-system config)))

(defn stop!
  []
  (close system))
