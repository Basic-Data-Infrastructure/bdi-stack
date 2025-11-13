;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.test.system-helpers
  "Defines a number of BDI systems that can be used for tests.

  Also includes request maps to use when accessing the services."
  (:require [buddy.core.keys :as keys]
            [clojure.java.io :as io]
            [org.bdinetwork.association-register.system :as association]
            [org.bdinetwork.authentication-service.system :as authentication-service]
            [org.bdinetwork.authentication.in-memory-association :as in-memory-association]
            [org.bdinetwork.authorization-register.system :as authorization]
            [org.bdinetwork.connector.system :as connector]
            [org.bdinetwork.test.helpers :refer [temp-dir]]
            [org.bdinetwork.test.oidc-helper :as oidc-helper]))

(def association-server-id "EU.EORI.ASSOCIATION-REGISTER")
(def association-server-port 9991)
(def association-server-url (str "http://localhost:" association-server-port))

(def association-config
  {:server-id                association-server-id
   :private-key              (keys/private-key (io/resource "test-config/association_register.key.pem"))
   :public-key               (keys/public-key (io/resource "test-config/association_register.cert.pem"))
   :x5c                      (authorization/x5c (io/resource "test-config/association_register.x5c.pem"))
   :data-source              (in-memory-association/read-source (io/resource "test-config/association-register-config.yml"))
   :port                     association-server-port
   :access-token-ttl-seconds 300})

(def authorization-server-id "EU.EORI.AUTHORIZATION-REGISTER")
(def authorization-server-port 9992)
(def authorization-server-url (str "http://localhost:" authorization-server-port))

(def authorization-config
  {:server-id                authorization-server-id
   :private-key              (keys/private-key (io/resource "test-config/authorization_register.key.pem"))
   :public-key               (keys/public-key (io/resource "test-config/authorization_register.cert.pem"))
   :x5c                      (authorization/x5c (io/resource "test-config/authorization_register.x5c.pem"))
   :port                     authorization-server-port
   :association-server-id    association-server-id
   :association-server-url   association-server-url
   :access-token-ttl-seconds 300})

(def authentication-server-id "EU.EORI.AUTHENTICATION-SERVICE")
(def authentication-server-port 9993)
(def authentication-server-url (str "http://localhost:" authentication-server-port))

(def authentication-server-config
  {:server-id                authentication-server-id
   :private-key              (keys/private-key (io/resource "test-config/authentication_service.key.pem"))
   :public-key               (keys/public-key (io/resource "test-config/authentication_service.cert.pem"))
   :x5c                      (authorization/x5c "test-config/authentication_service.x5c.pem")
   :port                     authentication-server-port
   :association-server-id    association-server-id
   :association-server-url   association-server-url
   :access-token-ttl-seconds 300})

(def client-id "EU.EORI.CLIENT")
(def dataspace-id "ORG.BDI.VGU-DEMO")

(def client-config
  {:ishare/client-id          client-id
   :ishare/dataspace-id       dataspace-id
   :ishare/private-key        (keys/private-key (io/resource "test-config/client.key.pem"))
   :ishare/x5c                (authorization/x5c (io/resource "test-config/client.x5c.pem"))
   :ishare/satellite-id       association-server-id
   :ishare/satellite-base-url association-server-url
   :throw                     false ;; we test on status codes, so don't throw in the tests
   })

(def data-owner-id  "EU.EORI.DATA-OWNER")

(def data-owner-config
  {:ishare/client-id          data-owner-id
   :ishare/dataspace-id       dataspace-id
   :ishare/private-key        (keys/private-key (io/resource "test-config/data_owner.key.pem"))
   :ishare/x5c                (authorization/x5c (io/resource "test-config/data_owner.x5c.pem"))
   :ishare/satellite-id       association-server-id
   :ishare/satellite-base-url association-server-url
   :throw                     false ;; we test on status codes, so don't throw in the tests
   })

(def static-backend-port 9994)
(def static-backend-url (str "http://localhost:" static-backend-port))

(def static-backend-config
  {:rules-file "test-config/static-backend.edn"
   :port static-backend-port
   :shutdown-timeout 0.1})

(def backend-connector-port 9995)
(def backend-connector-id "EU.EORI.CONNECTOR")
(def backend-connector-url (str "http://localhost:" backend-connector-port))

(def backend-connector-config
  {:rules-file "test-config/backend-connector.edn"
   :port backend-connector-port
   :shutdown-timeout 0.1})

(def association-server-request
  (assoc client-config
         :ishare/base-url association-server-url
         :ishare/server-id association-server-id))

(def authorization-server-request
  (assoc client-config
         :ishare/base-url authorization-server-url
         :ishare/server-id authorization-server-id))

(defn policy-request ;; non standard; create policy in AR
  ([delegation-evidence]
   (policy-request authorization-server-request delegation-evidence))
  ([request delegation-evidence]
   (-> request
       (assoc :method       :post
              :path         "policy"
              :as           :json
              :json-params  delegation-evidence
              :ishare/unsign-token "policy_token"
              :ishare/lens         [:body "policy_token"]))))

(def authentication-server-request
  (assoc client-config
         :ishare/base-url authentication-server-url
         :ishare/server-id authentication-server-id))

(def backend-connector-request
  (assoc client-config
         :ishare/base-url backend-connector-url
         :ishare/server-id backend-connector-id))

(def connector-config
  {:rules-file "test-config/connector-rules.edn"
   :port 9993})

(def openid-port 9996)
(def openid-host "localhost")
(def openid-url (str "http://" openid-host ":" openid-port))
(def openid-private-key (keys/private-key (io/resource "test-config/oidc.key.pem")))
(def openid-config
  {:host        openid-host
   :port        openid-port
   :private-key openid-private-key
   :public-key  (keys/public-key (io/resource "test-config/oidc.cert.pem"))})

;; These functions all return `nl.jomco.resource` systems, meaning
;; they can be used in a
;; `nl.jomco.resource/with-resources [foo (xxx-system)] ...)` call

(defn association-system
  []
  (association/run-system association-config))

(defn authorization-system
  []
  (authorization/run-system (assoc authorization-config :policies-directory (temp-dir))))

(defn authentication-system
  []
  (authentication-service/run-system authentication-server-config))

(defn static-backend-system
  []
  (connector/run-system static-backend-config))

(defn backend-connector-system
  []
  (connector/run-system backend-connector-config))

(defn oidc-system
  []
  (oidc-helper/openid-test-server openid-config))

(defn mk-oidc-access-token
  [{:keys [aud sub] :as claims}]
  {:pre [aud sub]}
  (oidc-helper/mk-token (assoc claims :iss openid-url) openid-private-key))

(def noodlebar-request
  {:ishare/server-adherent? true
   :ishare/base-url backend-connector-url
   :throw false})
