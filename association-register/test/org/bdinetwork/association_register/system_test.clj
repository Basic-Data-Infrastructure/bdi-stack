;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.system-test
  (:require [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.test.system-helpers :refer [association-system client-config]]))

(deftest test-system
  (with-resources [_s (association-system)]
    (let [{:keys [status] :as response} (client/exec (-> client-config
                                                         (request/party-request "EU.EORI.CLIENT")))]
      (is (= http-status/ok status))
      (is (= (get-in response [:ishare/result :party_info :party_id]) "EU.EORI.CLIENT")))

    (let [{:keys [status] :as response} (client/exec (-> client-config
                                                         (request/party-request "EU.EORI.NONE")))]
      (is (= http-status/ok status))
      ;; what should be the format when no party exists?
      (is (nil? (get-in response [:ishare/result :party_info :party_id]))))))
