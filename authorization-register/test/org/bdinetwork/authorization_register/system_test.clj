;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.system-test
  (:require [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.test.system-helpers :refer [association-system authorization-system client-config association-server-request authorization-server-request]]))

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
                                         :environment {:serviceProviders []}}}]}]}})

(def delegation-evidence
  {"policyIssuer" (:ishare/client-id client-config)
   "target"       {"accessSubject" "EU.EORI.NLPRECIOUSG"}
   "notBefore"    1
   "notOnOrAfter" 2
   "policySets"   [{"target" {"environment" {"licenses" ["0001"]}}
                    "policies" [{"rules"  [{"effect" "Permit"}]
                                 "target" {"resource"    {"type"        "klantordernummer"
                                                          "identifiers" ["112233"]
                                                          "attributes"  ["*"]}
                                           "actions"     ["BDI.PICKUP"]
                                           "environment" {"licenses"         ["0001"]
                                                          "serviceProviders" []}}}]}]})


(deftest system-test
  (with-resources [_association-system (association-system)
                   _authorization-system (authorization-system)]
    (let [resp (client/exec (request/access-token-request association-server-request))]
      (is (= http-status/ok (:status resp)))
      (is (string? (get-in resp [:body "access_token"]))))

    (let [resp  (client/exec (request/access-token-request authorization-server-request))
          token (get-in resp [:body "access_token"])]
      (is (= http-status/ok (:status resp)))
      (is (string? token))

      (let [resp (client/exec (policy-request {"delegationEvidence" delegation-evidence}))]
        (is (= http-status/ok (:status resp))
            "Policy accepted"))

      (testing "attempt to insert policy for other party"
        (let [resp (client/exec (policy-request {"delegationEvidence" (assoc delegation-evidence "policyIssuer" "someone-else")}))]
          (is (= http-status/forbidden (:status resp))
              "Policy rejected")))

      (let [resp (client/exec (request/delegation-evidence-request
                               authorization-server-request
                               delegation-mask))]
        (is (= http-status/ok (:status resp)))
        (is (= "Permit" (get-in resp [:ishare/result :delegationEvidence :policySets 0 :policies 0 :rules 0 :effect]))
            "Permit when matching policy found")))))
