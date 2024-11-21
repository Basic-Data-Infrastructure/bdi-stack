;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.system-test
  (:require [buddy.core.keys :as keys]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.association-register.system :as system]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.ring.in-memory-association :refer [read-source]]))

;; TODO: make port numbers configurable for tests / automatically use free port

(def client-config
  {:ishare/satellite-base-url "http://localhost:8080"
   :ishare/satellite-id       "EU.EORI.SERVER"
   :ishare/client-id          "EU.EORI.CLIENT"
   :ishare/private-key        (client/private-key "test-config/client.key.pem")
   :ishare/x5c                (system/x5c "test-config/client.x5c.pem")})

(def system-config
  {:private-key              (client/private-key "test-config/association_register.key.pem")
   :public-key               (keys/public-key "test-config/association_register.cert.pem")
   :x5c                      (system/x5c "test-config/association_register.x5c.pem")
   :data-source              (read-source "test-config/association-register-config.yml")
   :server-id                "EU.EORI.SERVER"
   :hostname                 "localhost"
   :port                     8080
   :access-token-ttl-seconds 600})

(deftest test-system
  (with-resources [#_:clj-kondo/ignore s (system/run-system system-config)]
    (let [{:keys [status] :as response} (client/exec (-> client-config
                                                         (request/party-request "EU.EORI.CLIENT")))]
      (is (= http-status/ok status))
      (is (= (get-in response [:ishare/result :party_info :party_id]) "EU.EORI.CLIENT")))

    (let [{:keys [status] :as response} (client/exec (-> client-config
                                                         (request/party-request "EU.EORI.NONE")))]
      (is (= http-status/ok status))
      ;; what should be the format when no party exists?
      (is (nil? (get-in response [:ishare/result :party_info :party_id]))))))
