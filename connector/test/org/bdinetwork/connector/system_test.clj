;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.system-test
  (:require

            [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.test.system-helpers :refer [association-system
                                                        authorization-system
                                                        client-config
                                                        association-server-request
                                                        authorization-server-request
                                                        ]]
            [passage.rules :refer [*default-aliases*]]
            [passage.system :as system]
            [org.bdinetwork.connector.main :as main]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.association-register.system :as association]
            [org.bdinetwork.authorization-register.system :as authorization]
            [org.bdinetwork.authorization-register.main :as authorization-register.main])
  (:import (java.nio.file Files)
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

      (let [resp (client/exec (policy-request (assoc authorization-server-request
                                                     :ishare/bearer-token token)
                                              {"delegationEvidence" delegation-evidence}))]
        (is (= http-status/ok (:status resp))
            "Policy accepted"))

      (testing "attempt to insert policy for other party"
        (let [resp (client/exec (-> (assoc authorization-server-request
                                           :ishare/bearer-token token)
                                    (policy-request {"delegationEvidence" (assoc delegation-evidence "policyIssuer" "someone-else")})))]
          (is (= http-status/forbidden (:status resp))
              "Policy rejected")))

      (let [resp (client/exec (request/delegation-evidence-request
                               (assoc authorization-server-request
                                      :ishare/bearer-token token)
                               delegation-mask))]
        (is (= http-status/ok (:status resp)))
        (is (= "Permit" (get-in resp [:ishare/result :delegationEvidence :policySets 0 :policies 0 :rules 0 :effect]))
            "Permit when matching policy found")))))
