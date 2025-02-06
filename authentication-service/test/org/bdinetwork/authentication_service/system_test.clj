;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication-service.system-test
  (:require [buddy.core.keys :as keys]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.association-register.system :as association]
            [org.bdinetwork.authentication-service.system :as system]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.ring.in-memory-association :refer [read-source]]))

(def association-config
  {:server-id                "EU.EORI.ASSOCIATION-REGISTER"
   :private-key              (keys/private-key "test-config/association_register.key.pem")
   :public-key               (keys/public-key "test-config/association_register.cert.pem")
   :x5c                      (system/x5c "test-config/association_register.x5c.pem")
   :data-source              (read-source "test-config/association-register-config.yml")
   :port                     9991
   :access-token-ttl-seconds 300})

(def auth-server-config
  {:server-id                "EU.EORI.AUTHENTICATION-SERVICE"
   :private-key              (keys/private-key "test-config/authentication_service.key.pem")
   :public-key               (keys/public-key "test-config/authentication_service.cert.pem")
   :x5c                      (system/x5c "test-config/authentication_service.x5c.pem")
   :port                     9992
   :association-server-id    (:server-id association-config)
   :association-server-url   (str "http://localhost:" (:port association-config))
   :access-token-ttl-seconds 300})

(def client-config
  {:ishare/client-id          "EU.EORI.CLIENT"
   :ishare/private-key        (keys/private-key "test-config/client.key.pem")
   :ishare/x5c                (system/x5c "test-config/client.x5c.pem")
   :ishare/satellite-id       (:server-id association-config)
   :ishare/satellite-base-url (str "http://localhost:" (:port association-config))
   :throw                     false ;; we test on status codes, so don't throw in the tests
   })


(deftest system-test
  #_{:clj-kondo/ignore [:unused-binding]}
  (with-resources [association-system (association/run-system association-config)
                   authentication-system (system/run-system auth-server-config)]
    (let [resp (client/exec (request/access-token-request (assoc client-config
                                                                 :ishare/base-url "http://localhost:9991"
                                                                 :ishare/server-id (:server-id association-config))))]
      (is (= http-status/ok (:status resp)))
      (is (string? (get-in resp [:body "access_token"]))))

    (let [resp  (client/exec (request/access-token-request (assoc client-config
                                                                  :ishare/base-url "http://localhost:9992"
                                                                  :ishare/server-id (:server-id auth-server-config))))
          token (get-in resp [:body "access_token"])]
      (is (= http-status/ok (:status resp)))
      (is (string? token)))))
