;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.gateway.interceptors :refer [->interceptor]]
            [org.bdinetwork.ishare.jwt :as ishare-jwt]
            [org.bdinetwork.service-commons.config :as config]
            [ring.util.codec :as ring-codec])
  (:import (java.io StringBufferInputStream)))

;; force loading BDI interceptor multi methods
#_{:clj-kondo/ignore [:unused-namespace]}
(require '[org.bdinetwork.connector.interceptors :as _bdi-interceptors])

(def server-id "EU.EORI.CONNECTOR")

(def connector-env
  {:server-id   server-id
   :private-key (io/resource "test-config/connector.key.pem")
   :public-key  (io/resource "test-config/connector.cert.pem")
   :x5c         (io/resource "test-config/connector.x5c.pem")})

(def config
  (-> connector-env
      (config/config config/server-party-opt-specs)
      (assoc :in-memory-association-data-source (io/resource "test-config/association-register-config.yml"))))

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

(deftest bdi-deauthenticate
  (let [{:keys [name enter]} (->interceptor ['bdi/deauthenticate] config)]
    (is (= "bdi/deauthenticate" name))

    (let [req  {:headers {"x-test" "test"}}
          req' {:headers {"x-test" "test", "x-bdi-client-id" "test"}}]
      (testing "without x-bdi-client-id request header"
        (is (= req (:request (enter {:request req})))))

      (testing "with x-bdi-client-id request header"
        (is (= req (:request (enter {:request req'}))))))))



(def client-id "EU.EORI.CLIENT")

(def client-env
  {:private-key (io/resource "test-config/client.key.pem")
   :x5c         (io/resource "test-config/client.x5c.pem")})

(def client-party-opt-specs
  {:private-key ["Client private key pem file" :private-key]
   :x5c         ["Client certificate chain pem file" :x5c]})

(defn form-params-encode [params]
  (->> params
       (ring-codec/form-encode)
       (StringBufferInputStream.)))

(deftest bdi-connect-token
  (let [{:keys [name enter]} (->interceptor ['bdi/connect-token] config)]
    (is (= "bdi/connect-token EU.EORI.CONNECTOR" name))
    (let [request            {:request-method :post, :headers {"content-type" "application/x-www-form-urlencoded"}}
          {:keys [response]} (enter {:request request})]
      (is (= http-status/bad-request (:status response)))

      (let [config             (config/config client-env client-party-opt-specs)
            client-assertion   (ishare-jwt/make-client-assertion {:ishare/client-id   client-id
                                                                  :ishare/server-id   server-id
                                                                  :ishare/x5c         (:x5c config)
                                                                  :ishare/private-key (:private-key config)})
            params             {:grant_type            "client_credentials"
                                :scope                 "iSHARE"
                                :client_id             client-id
                                :client_assertion_type "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                                :client_assertion      client-assertion}
            request            (assoc request
                                      :body (form-params-encode params))
            {:keys [response]} (enter {:request request})]

        (is (= http-status/ok (:status response)))
        (is (string/starts-with? (get-in response [:headers "Content-Type"]) ;; ring-json sends camelcase header
                                 "application/json"))

        (let [{:strs [token_type]} (json/read-str (:body response))]
          (is (= "Bearer" token_type)))))))
