;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication-service.system-test
  (:require [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.test.system-helpers :refer [association-system authentication-system association-server-request authentication-server-request]]))

(deftest system-test
  (with-resources [_association-system (association-system)
                   _authentication-system (authentication-system)]
    (let [resp (client/exec (request/access-token-request association-server-request))]
      (is (= http-status/ok (:status resp)))
      (is (string? (get-in resp [:body "access_token"]))))

    (let [resp  (client/exec (request/access-token-request authentication-server-request))
          token (get-in resp [:body "access_token"])]
      (is (= http-status/ok (:status resp)))
      (is (string? token)))))
