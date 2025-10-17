;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.reverse-proxy.service-unavailable-test
  (:require [aleph.http :as http]
            [clojure.test :refer [deftest is use-fixtures]]
            [manifold.deferred :as d]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.gateway.response :as response]
            [org.bdinetwork.gateway.reverse-proxy :as sut]
            [org.bdinetwork.test-helper :refer [backend-url proxy-url start-proxy]]))

(defn- handler [req]
  (d/catch
      (sut/proxy-request (assoc req :url backend-url))
      (fn [_] response/service-unavailable)))

(use-fixtures :once
  (fn [f]
    (with-resources [_proxy (start-proxy handler)]
      (f))))

(deftest service-unavailable
  (let [{:keys [status]} @(http/get proxy-url {:throw-exceptions? false})]
    (is (= http-status/service-unavailable status)
        "service unavailable")))
