(ns user
  (:require [nl.jomco.resources :refer [defresource close]]
            [org.bdinetwork.assocation-register.system :as system]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.assocation-register.data-source :as ds]
            [buddy.core.keys :as keys]
            [nl.jomco.resources :refer [defresource close]]))

(defn system-config
  []
  {:private-key              (client/private-key "test/pem/server.key.pem")
   :public-key               (keys/public-key "test/pem/server.cert.pem")
   :x5c                      (system/x5c "test/pem/server.x5c.pem")
   :data-source              (ds/yaml-in-memory-data-source-factory "test/test-config.yml")
   :server-id                "EU.EORI.SERVER"
   :hostname                 "localhost"
   :port                     8080
   :access-token-ttl-seconds 600})

(defresource system)

(defn start!
  []
  (defresource system (system/run-system (system-config))))

(defn stop!
  []
  (when system
    (close system)))
