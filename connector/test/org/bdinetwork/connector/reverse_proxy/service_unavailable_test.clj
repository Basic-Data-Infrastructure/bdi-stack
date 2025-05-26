;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy.service-unavailable-test
  (:require [aleph.http :as http]
            [clojure.test :refer [deftest is use-fixtures]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.connector.reverse-proxy :as sut]
            [org.bdinetwork.connector.reverse-proxy-test-helper :refer [backend-url proxy-url start-proxy]]))

(use-fixtures :once
  (fn [f]
    (with-resources [_proxy (start-proxy (sut/make-target-rewrite-fn backend-url))]
      (f))))

(deftest service-unavailable
  (let [{:keys [status]} @(http/get proxy-url {:throw-exceptions? false})]
    (is (= http-status/service-unavailable status)
        "service unavailable")))
