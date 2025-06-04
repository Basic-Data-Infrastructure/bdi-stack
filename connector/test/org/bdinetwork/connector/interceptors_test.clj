;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors-test
  (:require [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.gateway.interceptors :refer [->interceptor]]
            [org.bdinetwork.service-commons.config :as config]))

;; force loading BDI interceptor multi methods
#_{:clj-kondo/ignore [:unused-namespace]}
(require '[org.bdinetwork.connector.interceptors :as _bdi-interceptors])

(def connector-env
  {:private-key "test-config/connector.key.pem"
   :public-key  "test-config/connector.cert.pem"
   :x5c         "test-config/connector.x5c.pem"
   :server-id   "EU.EORI.CONNECTOR"})

(def config (config/config connector-env config/server-party-opt-specs))

(defn mk-access-token [client-id]
  (access-token/mk-access-token (assoc config :client-id client-id)))

(deftest bdi-authenticate
  (let [{:keys [name enter]} (->interceptor ['bdi/authenticate] config)]
    (is (= "bdi/authenticate EU.EORI.CONNECTOR" name))

    (testing "without token"
      (let [{:keys [response]} (enter {:request {}})]
        (is response "got an immediate response")
        (is (= http-status/unauthorized (:status response)))
        (is (= "Bearer scope=\"BDI\"" (get-in response [:headers "www-authenticate"])))))

    (testing "bad token"
      (let [{:keys [response]} (enter {:request {:headers {"authorization" "Bearer bad-token"}}})]
        (is response "got an immediate response")
        (is (= http-status/unauthorized (:status response)))
        (is (= "Bearer scope=\"BDI\"" (get-in response [:headers "www-authenticate"])))))

    (testing "valid token"
      (let [client-id          "EU.EORI.CLIENT"
            token              (mk-access-token client-id)
            {:keys [request
                    response]} (enter {:request {:headers {"authorization" (str "Bearer " token)}}})]
        (is (not response) "no response yet")
        (is (= client-id (get-in request [:headers "x-bdi-client-id"])))))))
