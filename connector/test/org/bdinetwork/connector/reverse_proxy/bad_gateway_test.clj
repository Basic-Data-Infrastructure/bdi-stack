;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy.bad-gateway-test
  (:require [aleph.http :as http]
            [clojure.test :refer [deftest is use-fixtures]]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.connector.reverse-proxy-test-helper :refer [proxy-url start-proxy stop-proxy]]))

(use-fixtures :once
  (fn [f]
    (let [proxy (start-proxy (fn [& args] (throw (ex-info "boom!" args))))]
      (try (f) (finally (stop-proxy proxy))))))

(deftest bad-gateway
  (let [{:keys [status]} @(http/get proxy-url {:throw-exceptions? false})]
    (is (= http-status/bad-gateway status)
        "got bad gateway")))
