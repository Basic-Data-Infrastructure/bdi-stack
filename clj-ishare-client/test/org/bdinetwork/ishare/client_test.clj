;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client-test
  (:require [clojure.data.json :as json]
            [clojure.string :as s]
            [clojure.test :refer [deftest is testing are]]
            [clojure.java.io :as io]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.jwt :as jwt]
            [org.bdinetwork.ishare.test-helper :refer [run-exec take-request! put-response!]]))

(defn- ->x5c [v]
  (->> [v "ca"]
       (map #(str "pem/" % ".cert.pem"))
       (mapcat (comp client/x5c io/resource))))

(defn- ->key [v]
  (-> (str "pem/" v ".key.pem") io/resource client/private-key))

(def client-eori "EU.EORI.CLIENT")
(def client-x5c (->x5c "client"))
(def client-private-key (->key "client"))

(def satellite-eori "EU.EORI.AA")
(def satellite-url "https://satellite.example.com")
(def satellite-x5c (->x5c "aa"))
(def satellite-private-key (->key "aa"))

(def ar-eori "EU.EORI.AR")
(def ar-url "https://ar.example.com")
(def ar-x5c (->x5c "ar"))
(def ar-private-key (->key "ar"))

(def dataspace-id "test")

(def client-data
  {:ishare/client-id          client-eori
   :ishare/private-key        client-private-key
   :ishare/x5c                client-x5c
   :ishare/dataspace-id       dataspace-id
   :ishare/satellite-id       satellite-eori
   :ishare/satellite-base-url satellite-url})

(defn test-get-token [c base-url token]
  (testing (str "Getting an access token for " base-url)
    (let [{:keys [uri exception]} (take-request! c)]
      (when exception
        (throw (ex-info "Unexpected exception during access-token call"
                        {:token token}
                        exception)))
      (is (= (str base-url "/connect/token") (str uri))
          "Correct url for access token")

      (put-response! c {:status  200
                        :uri     uri
                        :headers {"content-type" "application/json"}
                        :body    (json/json-str {"access_token" token
                                                 "token_type"   "Bearer",
                                                 "expires_in"   3600})}))))

(deftest parties
  (testing "expired parties token"
    (let [[c r] (run-exec (client/parties-request client-data nil))]

      (test-get-token c satellite-url "satellite-token")

      (testing "Getting parties"
        (let [{:keys [uri] :as req} (take-request! c)]
          (is (= (str satellite-url "/parties")
                 (str uri) ;; is a java.net.URI
                 ))
          (is (= "Bearer satellite-token" (get-in req [:headers "Authorization"])))

          (put-response! c {:status  200
                            :uri     uri
                            :headers {"content-type" "application/json"}
                            :body    (json/json-str
                                      {"parties_token"
                                       (jwt/make-jwt {:iat 0
                                                      ;; Too old; will be rejected, since make-jwt
                                                      ;; will set exp to iat + 30 seconds.
                                                      :iss satellite-eori
                                                      :sub satellite-eori
                                                      :aud client-eori}
                                                     satellite-private-key
                                                     satellite-x5c)})})))

      (let [{:keys [exception]} (take-request! c)]
        (is exception
            "Exception raised")
        (is (s/starts-with? (ex-message exception)
                            "Token is expired")))

      (is (nil? @r))))

  (testing "wrong certificate chain"
    (let [[c r] (run-exec (client/parties-request client-data {"active_only" "true"}))]

      (test-get-token c satellite-url "satellite-token")

      (testing "getting parties"
        (let [{:keys [uri] :as req} (take-request! c)]
          (is (= (str satellite-url "/parties?active_only=true") (str uri)))
          (is (= "Bearer satellite-token" (get-in req [:headers "Authorization"])))

          (put-response! c {:status  200
                            :uri     (:uri req)
                            :headers {"content-type" "application/json"}
                            :body    (json/json-str
                                      {"parties_token"
                                       (jwt/make-jwt {:iss satellite-eori
                                                      :sub satellite-eori
                                                      :aud client-eori}
                                                     satellite-private-key
                                                     ;; we use `client-x5c`
                                                     ;; instead of `satellite-x5c`, so
                                                     ;; the certificate chain
                                                     ;; does not match the
                                                     ;; private key.
                                                     ;;
                                                     ;; This should raise an
                                                     ;; exception in the client
                                                     client-x5c)})})))

      (let [{:keys [exception]} (take-request! c)]
        (is exception
            "Exception raised")
        (is (= "Message seems corrupt or manipulated"
               (ex-message exception))))

      (is (nil? @r))))

  (testing "valid parties token"
    (let [[c r] (run-exec (client/parties-request client-data {"name" "Party Name"}))]

      (test-get-token c satellite-url "satellite-token")

      (testing "getting parties"
        (let [{:keys [uri] :as req} (take-request! c)]
          (is (= (str satellite-url "/parties?name=Party+Name") (str uri)))
          (is (= "Bearer satellite-token" (-> req :headers (get "Authorization"))))

          (put-response! c {:status  200
                            :uri     (:uri req)
                            :headers {"content-type" "application/json"}
                            :body    (json/json-str
                                      {"parties_token"
                                       (jwt/make-jwt {:iss          satellite-eori
                                                      :sub          satellite-eori
                                                      :aud          client-eori
                                                      :parties_info {:total_count 1,
                                                                     :pageCount   1,
                                                                     :count       1,
                                                                     :data        [{:party_id  "EU.EORI.CLIENT"
                                                                                    :adherence {:status     "Active"
                                                                                                :start_date "2024-10-01T07:40:25.597636Z"
                                                                                                :end_date   "2124-10-01T07:40:25.597636Z"}}]}}
                                                     satellite-private-key
                                                     satellite-x5c)})})))

      (is (= 1 (-> @r :ishare/result :parties_info :count))))))

(deftest delegation
  (testing "Delegation evidence request"

    (let [[c r] (run-exec (client/delegation-evidence-request (assoc client-data
                                                                     ;; use fresh cached fetch-party-info-fn for repeatable tests
                                                                     :ishare/fetch-party-info-fn (client/mk-cached-fetch-party-info 60000))
                                                              {:delegationRequest
                                                               {:policyIssuer client-eori}}))]
      (test-get-token c satellite-url "satellite-token")

      (testing "Getting party info to retreive AR location"
        (let [{:keys [uri] :as req} (take-request! c)]
          (is (= (str satellite-url "/parties/" client-eori) (str uri)))
          (is (= "Bearer satellite-token" (-> req :headers (get "Authorization"))))

          (put-response! c {:status  200
                            :uri     (:uri req)
                            :headers {"content-type" "application/json"}
                            :body    (json/json-str
                                      {"party_token"
                                       (jwt/make-jwt {:iss        satellite-eori
                                                      :sub        satellite-eori
                                                      :aud        client-eori
                                                      :party_info {:authregistery [{:dataspaceID              "other ds-id"
                                                                                    :authorizationRegistryID  "EU.EORI.OTHER"
                                                                                    :authorizationRegistryUrl "https://other.example.com"}
                                                                                   {:dataspaceID              dataspace-id
                                                                                    :authorizationRegistryID  ar-eori
                                                                                    :authorizationRegistryUrl ar-url}
                                                                                   {:dataspaceID              "random ds-id"
                                                                                    :authorizationRegistryID  "EU.EORI.RANDOM"
                                                                                    :authorizationRegistryUrl "https://random.example.com"}]}}
                                                     satellite-private-key
                                                     satellite-x5c)})})))

      (testing "Get AR provider's party info"
        (test-get-token c satellite-url "satellite-token")

        (let [{:keys [uri] :as req} (take-request! c)]
          (is (= (str satellite-url "/parties/" ar-eori) (str uri)))
          (is (= "Bearer satellite-token" (-> req :headers (get "Authorization"))))

          (put-response! c {:status  200
                            :headers {"content-type" "application/json"}
                            :body    (json/json-str
                                      {"party_token"
                                       (jwt/make-jwt {:iss        satellite-eori
                                                      :sub        satellite-eori
                                                      :aud        client-eori
                                                      :party_info {
                                                                   :adherence {:status     "Active"
                                                                               :start_date "2024-10-01T07:40:25.597636Z"
                                                                               :end_date   "2124-10-01T07:40:25.597636Z"}}}
                                                     satellite-private-key
                                                     satellite-x5c)})})))

      (testing "Get delegation evidence from AR"
        (test-get-token c ar-url "ar-token")

        (let [{:keys [uri] :as req} (take-request! c)]
          (is (= (str ar-url "/delegation") (str uri)))
          (is (= "Bearer ar-token" (get-in req [:headers "Authorization"])))

          (put-response! c {:status  200
                            :uri     uri
                            :headers {"content-type" "application/json"}
                            :body    (json/json-str
                                      {"delegation_token"
                                       (jwt/make-jwt {:iss                ar-eori
                                                      :sub                ar-eori
                                                      :aud                client-eori
                                                      :delegationEvidence "test"}
                                                     ar-private-key
                                                     ar-x5c)})})))

      (is (= "test" (-> @r :ishare/result :delegationEvidence))))))

(def base-request
  (assoc client-data
         :ishare/base-url "https://example.com/api"
         :ishare/server-id "EU.EORI.SERVERID"))

(def delegation-request
  {:delegationRequest {:policyIssuer client-eori}})

(defn clean-request
  [req]
  (-> req
      (dissoc :ishare/message-type
              :ishare/params
              :ishare/party-id)
      (update :form-params dissoc "client_assertion")))

(deftest deprecated-api
  (testing "Deprecated ishare->http-request middleware still works"
    (are [new-api old-api] (= (clean-request new-api)
                              (clean-request old-api))

      (client/access-token-request base-request)
      (client/ishare->http-request (assoc base-request
                                          :ishare/message-type :access-token))

      (client/parties-request base-request {"name" "foo"})
      (client/ishare->http-request (assoc base-request
                                          :ishare/message-type :parties
                                          :ishare/params {"name" "foo"}))

      (client/party-request base-request "foo")
      (client/ishare->http-request (assoc base-request
                                          :ishare/message-type :party
                                          :ishare/party-id "foo"))

      (client/trusted-list-request base-request)
      (client/ishare->http-request (assoc base-request
                                          :ishare/message-type :trusted-list))

      (client/capabilities-request base-request)
      (client/ishare->http-request (assoc base-request
                                          :ishare/message-type :capabilities))

      (client/delegation-evidence-request base-request delegation-request)
      (client/ishare->http-request (assoc base-request
                                          :ishare/message-type :delegation
                                          :ishare/policy-issuer client-eori
                                          :ishare/params delegation-request)))))
