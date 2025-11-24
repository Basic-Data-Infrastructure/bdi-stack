;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors-test
  (:require [aleph.http :as http]
            [buddy.core.keys :as keys]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.connector.interceptors :as interceptors]
            [org.bdinetwork.ishare.jwt :as ishare-jwt]
            [org.bdinetwork.service-commons.config :as config]
            [org.bdinetwork.test-helper :refer [jwks-keys mk-token openid-token-uri openid-uri proxy-url start-backend start-openid start-proxy]]
            [org.bdinetwork.test.system-helpers
             :refer [association-system
                     association-server-id
                     association-server-url]]
            [passage :as gateway]
            [passage.interceptors]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.codec :as ring-codec])
  (:import (java.io StringBufferInputStream)
           (java.time Instant)))

(def server-id "EU.EORI.CONNECTOR")

(def client-config
  {:client-id   server-id
   :private-key (keys/private-key (io/resource "test-config/connector.key.pem"))
   :public-key  (keys/public-key (io/resource "test-config/connector.cert.pem"))
   :x5c         (config/split-x5c (io/resource "test-config/connector.x5c.pem"))})

(def config
  (merge client-config
         {:server-id server-id
          :in-memory-association-data-source
          (io/resource "test-config/association-register-config.yml")}))

(defn mk-access-token [client-id]
  (access-token/mk-access-token (assoc config :client-id client-id)))

(deftest authenticate
  (let [{:keys [enter]} (interceptors/authenticate config)]

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

(deftest deauthenticate
  (let [{:keys [enter]} interceptors/deauthenticate
        req  {:headers {"x-test" "test"}}
        req' {:headers {"x-test" "test", "x-bdi-client-id" "test"}}]
    (testing "without x-bdi-client-id request header"
      (is (= req (:request (enter {:request req})))))

    (testing "with x-bdi-client-id request header"
      (is (= req (:request (enter {:request req'})))))))



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

(deftest connect-token
  (let [{:keys [enter]} (interceptors/connect-token config)
        request            {:request-method :post, :headers {"content-type" "application/x-www-form-urlencoded"}}
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
        (is (= "Bearer" token_type))))))



(deftest set-bearer-token
  (with-resources [_association-system (association-system)]
    (let [{:keys [enter]} interceptors/set-bearer-token
          config          (assoc client-config
                                 :server-id association-server-id
                                 :base-url association-server-url
                                 :association-id association-server-id
                                 :association-url association-server-url)
          ctx             (enter {} config)]
      (is (re-matches #"Bearer eyJ.+" ;; JWTs always start with 'eyJ' because it's base64 JSON '{"'
                      (get-in ctx [:request :headers "authorization"]))))))



(def ^:private noodlebar-host "localhost")
(def ^:private noodlebar-port 11003)
(def ^:private noodlebar-uri (str "http://" noodlebar-host ":" noodlebar-port))

(defn- start-noodlebar [evidence]
  (run-jetty (fn [_]
               {:status  http-status/ok
                :headers {"content-type" "application/json"}
                :body    (json/write-str evidence)})
             {:host  noodlebar-host
              :port  noodlebar-port
              :join? false}))

(deftest noodlebar-delegation
  (with-resources
      [_backend (start-backend (fn [req]
                                 {:status  http-status/ok
                                  :headers {"content-type" "application/edn"}
                                  :body    (-> req
                                               (select-keys [:request-method :uri :headers :body])
                                               (update :body slurp)
                                               (pr-str))}))
       _proxy   (start-proxy (gateway/make-gateway
                              {:rules [{:match {:uri "/"}
                                        :interceptors
                                        [[(list interceptors/noodlebar-delegation
                                           {:oauth2/token-url     openid-token-uri
                                            :oauth2/client-id     "dummy"
                                            :oauth2/client-secret "dummy"
                                            :oauth2/audience      "test-subject"
                                            :coremanager-url      noodlebar-uri})
                                          {:policyIssuer "test-issuer"
                                           :target       {:accessSubject "test-subject"}
                                           :policySets
                                           [{:policies
                                             [{:rules [{:effect "Permit"}]
                                               :target
                                               {:resource {:type        "test"
                                                           :identifiers ["*"]
                                                           :attributes  ["*"]}
                                                :actions  ["read"]
                                                :environment
                                                {:serviceProviders ["test-provider"]}}}]}]}]
                                         [passage.interceptors/respond
                                          {:status 200
                                           :body   "pass"}]]}]}))
       _openid    (start-openid jwks-keys)
       _noodlebar (start-noodlebar
                   {:policyIssuer "test-issuer"
                    :target       {"accessSubject" "test-subject"}
                    :notBefore    0
                    :notOnOrAfter (+ (.getEpochSecond (Instant/now)) 60)
                    :policySets   [{:target   {:environment {:licenses ["0001"]}}
                                    :policies [{:rules  [{:effect "Permit"}]
                                                :target {:resource    {:type        "test"
                                                                       :identifiers ["*"]
                                                                       :attributes  ["*"]}
                                                         :actions     ["read"]
                                                         :environment {:serviceProviders ["test-provider"]}}}]}]})]
    (testing "success"
      (let [token (mk-token {:iat (.getEpochSecond (Instant/now))
                             :iss openid-uri
                             :aud "audience"
                             :sub "test-subject"})
            {:keys [status body]}
            @(http/get proxy-url
                       {:throw-exceptions? false
                        :headers           {"authorization" (str "Bearer " token)}})]
        (is (= http-status/ok status))
        (is (= "pass"
               (slurp body)))))))
