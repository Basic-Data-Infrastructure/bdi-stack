;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.validate-delegation-test
  (:require [buddy.core.keys :as keys]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.association-register.system :as association]
            [org.bdinetwork.authentication.in-memory-association :refer [read-source]]
            [org.bdinetwork.authorization-register.system :as system]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.ishare.client.validate-delegation :as validate-delegation])
  (:import (java.time Instant)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn- own-ar-request
  [{:ishare/keys [authorization-registry-id
                  authorization-registry-base-url
                  base-url
                  server-id]
    :as          request}]
  (if (and base-url server-id)
    request
    (assoc request
           :ishare/base-url  authorization-registry-base-url
           :ishare/server-id authorization-registry-id)))

(defn policy-request ;; non-standard request
  [request delegation-evidence]
  {:pre [delegation-evidence]}
  (-> request
      (own-ar-request)
      (assoc :method       :post
             :path         "policy"
             :as           :json
             :json-params  delegation-evidence
             :ishare/unsign-token "policy_token"
             :ishare/lens         [:body "policy_token"])))

(def association-config
  {:server-id                "EU.EORI.ASSOCIATION-REGISTER"
   :private-key              (keys/private-key (io/resource "test-config/association_register.key.pem"))
   :public-key               (keys/public-key (io/resource "test-config/association_register.cert.pem"))
   :x5c                      (system/x5c (io/resource "test-config/association_register.x5c.pem"))
   :data-source              (read-source (io/resource "test-config/association-register-config.yml"))
   :port                     9991
   :access-token-ttl-seconds 300})

(def auth-register-config
  {:server-id                "EU.EORI.AUTHORIZATION-REGISTER"
   :private-key              (keys/private-key (io/resource "test-config/authorization_register.key.pem"))
   :public-key               (keys/public-key (io/resource "test-config/authorization_register.cert.pem"))
   :x5c                      (system/x5c (io/resource "test-config/authorization_register.x5c.pem"))
   :port                     9992
   :association-server-id    (:server-id association-config)
   :association-server-url   (str "http://localhost:" (:port association-config))
   :access-token-ttl-seconds 300})

(def client-config
  {:ishare/client-id          "EU.EORI.CLIENT"
   :ishare/dataspace-id       "ORG.BDI.VGU-DEMO"
   :ishare/private-key        (keys/private-key (io/resource "test-config/client.key.pem"))
   :ishare/x5c                (system/x5c (io/resource "test-config/client.x5c.pem"))
   :ishare/satellite-id       (:server-id association-config)
   :ishare/satellite-base-url (str "http://localhost:" (:port association-config))
   :throw                     false ;; we test on status codes, so don't throw in the tests
   })

(def delegation-mask
  {:delegationRequest
   {:policyIssuer (:ishare/client-id client-config)
    :target       {:accessSubject "EU.EORI.NLPRECIOUSG"}
    :policySets   [{:policies [{:rules  [{:effect "Permit"}]
                                :target {:resource    {:type        "klantordernummer"
                                                       :identifiers ["112233"]
                                                       :attributes  ["*"]}
                                         :actions     ["BDI.PICKUP"]
                                         :environment {:serviceProviders ["SP1"]}}}]}]}})

(def policy-selector
  {:policy/issuer (:ishare/client-id client-config)
   :policy/access-subject "EU.EORI.NLPRECIOUSG"
   :policy/actions ["BDI.PICKUP"]
   :policy/resource-type "klantordernummer"
   :policy/resource-identifiers ["112233"]
   :policy/resource-attributes ["*"]
   :policy/service-providers ["SP1"]})

(deftest conversions
  (is (= delegation-mask
         {:delegationRequest (validate-delegation/policy-selector->delegation-mask policy-selector)})))

(def now
   (.getEpochSecond (Instant/now)))

(def not-before
  (- (.getEpochSecond (Instant/now)) 1000))

(def not-after
  (+ (.getEpochSecond (Instant/now)) 1000))

(deftest max-delegation-depth
  (is (nil? (validate-delegation/policy-chain-mismatch
             now
             {:policy/max-delegation-depth 2}
             [{:policy/max-delegation-depth 2}
              {:policy/max-delegation-depth 1}])))
  (is (validate-delegation/policy-chain-mismatch
       now
       {:policy/max-delegation-depth 1}
       [{:policy/max-delegation-depth 2}
        {:policy/max-delegation-depth 1}]))
  (is (nil? (validate-delegation/policy-chain-mismatch
             now
             {}
             [{:policy/max-delegation-depth 2}
              {:policy/max-delegation-depth 1}])))
  (is (validate-delegation/policy-chain-mismatch
       now
       {}
       [{:policy/max-delegation-depth 1}
        {:policy/max-delegation-depth 1}]))
  (is (validate-delegation/policy-chain-mismatch
       now
       {:policy/max-delegation-depth 10}
       [{:policy/max-delegation-depth 1}
        {:policy/max-delegation-depth 2}]))
  (is (validate-delegation/policy-chain-mismatch
       now
       {:policy/max-delegation-depth 10}
       [{:policy/max-delegation-depth 2}
        {:policy/max-delegation-depth 2}
        {:policy/max-delegation-depth 1}])))

(def delegation-evidence
  {:policyIssuer (:ishare/client-id client-config)
   :target       {"accessSubject" "EU.EORI.NLPRECIOUSG"}
   :notBefore    not-before
   :notOnOrAfter not-after
   :policySets   [{:target {:environment {:licenses ["0001"]}}
                   :policies [{:rules  [{:effect "Permit"}]
                               :target {:resource    {:type        "klantordernummer"
                                                      :identifiers ["112233"]
                                                      :attributes  ["*"]}
                                        :actions     ["BDI.PICKUP"]
                                        :environment {:licenses         ["0001"]
                                                      :serviceProviders []}}}]}]})

(defn temp-dir
  []
  (str (Files/createTempDirectory "authregisterdb" (make-array FileAttribute 0))))


(def ar-request
  (assoc client-config
         :ishare/base-url "http://localhost:9992"
         :ishare/server-id (:server-id auth-register-config)))

(deftest system-test
  (let [dir (temp-dir)]
    (with-resources [_association-system (association/run-system association-config)
                     _authorization-system (system/run-system (assoc auth-register-config
                                                                     :policies-directory dir))]

      (let [resp (client/exec (policy-request ar-request
                                              {"delegationEvidence" delegation-evidence}))]
        (is (= http-status/ok (:status resp))
            "Policy accepted"))

      (let [resp (client/exec (request/delegation-evidence-request ar-request delegation-mask))]
        (is (= http-status/ok (:status resp)))
        (is (= "Permit" (get-in resp [:ishare/result :delegationEvidence :policySets 0 :policies 0 :rules 0 :effect]))
            "Permit when matching policy found"))

      (is (nil? (validate-delegation/fetch-and-validate-delegation client-config
                                                                   policy-selector
                                                                   ["EU.EORI.CLIENT"
                                                                    "EU.EORI.NLPRECIOUSG"])))

      (is (validate-delegation/fetch-and-validate-delegation client-config
                                                             (assoc policy-selector :policy/resource-type "something else")
                                                             ["EU.EORI.CLIENT"
                                                              "EU.EORI.NLPRECIOUSG"])))))
