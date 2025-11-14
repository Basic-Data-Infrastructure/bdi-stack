;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.validate-delegation-test
  (:require [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.ishare.client.validate-delegation :as validate-delegation]
            [org.bdinetwork.test.system-helpers :refer [association-system authorization-system client-config authorization-server-request]])
  (:import (java.time Instant)))

(defn policy-request ;; non-standard request
  [delegation-evidence]
  {:pre [delegation-evidence]}
  (-> authorization-server-request
      (assoc :method       :post
             :path         "policy"
             :as           :json
             :json-params  delegation-evidence
             :ishare/unsign-token "policy_token"
             :ishare/lens         [:body "policy_token"])))

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

(deftest system-test
  (with-resources [_association-system (association-system)
                   _authorization-system (authorization-system)]

    (let [resp (client/exec (policy-request {"delegationEvidence" delegation-evidence}))]
      (is (= http-status/ok (:status resp))
          "Policy accepted"))

    (let [resp (client/exec (request/delegation-evidence-request authorization-server-request delegation-mask))]
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
                                                            "EU.EORI.NLPRECIOUSG"]))))
