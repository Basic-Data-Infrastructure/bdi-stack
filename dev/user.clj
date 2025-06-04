;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(in-ns 'clojure.core)

(defn pk
  "Peek value for debugging."
  ([v] (prn v) v)
  ([k v] (prn k v) v))

(defn pk->
  "Peek value for debugging."
  ([v] (prn v) v)
  ([v k] (prn k v) v))

(ns user
  (:require [nl.jomco.resources :refer [defresource close mk-system]]
            [org.bdinetwork.association-register.main :as association-register.main]
            [org.bdinetwork.authentication-service.main :as authentication-service.main]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.authorization-register.main :as authorization-register.main]
            [org.bdinetwork.connector.main :as connector.main]
            [org.bdinetwork.service-commons.config :as config]))

(def association-env
  {:private-key            "test-config/association_register.key.pem"
   :public-key             "test-config/association_register.cert.pem"
   :x5c                    "test-config/association_register.x5c.pem"
   :server-id              "EU.EORI.ASSOCIATION-REGISTER"

   :data-source            "test-config/association-register-config.yml"
   :port                   "8880"})

(def authentication-env
  {:private-key            "test-config/authentication_service.key.pem"
   :public-key             "test-config/authentication_service.cert.pem"
   :x5c                    "test-config/authentication_service.x5c.pem"
   :server-id              "EU.EORI.AUTHENTICATION-SERVICE"
   :association-server-id  "EU.EORI.ASSOCIATION-REGISTER"
   :association-server-url (str "http://localhost:" (:port association-env))

   :port                   "8881"})

(def authorization-env
  {:private-key            "test-config/authorization_register.key.pem"
   :public-key             "test-config/authorization_register.cert.pem"
   :x5c                    "test-config/authorization_register.x5c.pem"
   :server-id              "EU.EORI.AUTHORIZATION-REGISTER"
   :association-server-id  "EU.EORI.ASSOCIATION-REGISTER"
   :association-server-url (str "http://localhost:" (:port association-env))

   :port                   "8882"
   :policies-db            "policies.db"})

(def connector-env
  {:private-key            "test-config/connector.key.pem"
   :public-key             "test-config/connector.cert.pem"
   :x5c                    "test-config/connector.x5c.pem"
   :server-id              "EU.EORI.CONNECTOR"
   :association-server-id  "EU.EORI.ASSOCIATION-REGISTER"
   :association-server-url (str "http://localhost:" (:port association-env))

   :rules-file       "rules.edn"
   :hostname         "localhost"
   :port             "8081"})

#_{:clj-kondo/ignore [:uninitialized-var]}
(defresource system)

(defn start! []
  #_{:clj-kondo/ignore [:inline-def :unused-binding]}
  (defresource system
    (mk-system [association (association-register.main/start association-env)
                authentication (authentication-service.main/start authentication-env)
                authorization (authorization-register.main/start authorization-env)
                connector (connector.main/start connector-env)])))

(defn stop! []
  (close system))

(defn mk-access-token [client-id server-env]
  (let [cnf (config/config server-env config/server-party-opt-specs)]
    (access-token/mk-access-token (assoc cnf :client-id client-id))))
