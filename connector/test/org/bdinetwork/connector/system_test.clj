;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.system-test
  (:require [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.test.system-helpers :refer [association-system
                                                        authorization-system
                                                        client-config
                                                        client-id
                                                        data-owner-config
                                                        data-owner-id
                                                        backend-connector-request
                                                        policy-request
                                                        authorization-server-id
                                                        authorization-server-url
                                                        static-backend-system
                                                        backend-connector-system]]))

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

(defn now
  []
  (.getEpochSecond (java.time.Instant/now)))

(defn next-hour
  []
  (+ (now) (* 60 60)))

(deftest system-test
  (with-resources [_association-system (association-system)
                   _authorization-system (authorization-system)
                   _backend (static-backend-system)
                   _backend-connector (backend-connector-system)]
    (testing "authentication"
      (testing "fetching access token"
        (let [resp  (client/exec (request/access-token-request backend-connector-request))
              token (get-in resp [:body "access_token"])]
          (is (= http-status/ok (:status resp))
              "status ok")
          (is (string? token)
              "access token present")))

      (testing "accessing authenticated backend"
        (testing "using provided access token"
          (let [resp (client/exec (assoc backend-connector-request
                                         :method :get
                                         :path "/api/authenticated"))]
            (is (= http-status/ok (:status resp))
                "status ok"))))

      (testing "using fake access token"
        (is (= http-status/unauthorized
               (:status (client/exec (assoc backend-connector-request
                                            :method :get
                                            :path "/api/authenticated"
                                            :ishare/bearer-token "NONSENSE"))))
            "status unauthorized")))

    (testing "authorization"
      (testing "accessing authorzed backend"
        (testing "without correct policy"
          (let [resp (client/exec (assoc backend-connector-request
                                         :method :get
                                         :path "/api/authorized"))]
            (is (= http-status/forbidden (:status resp))
                "status ok")))
        (testing "with correct policy"
          (is (= http-status/ok 
                 (:status (client/exec (-> data-owner-config
                                           (assoc :ishare/server-id authorization-server-id
                                                  :ishare/base-url authorization-server-url)
                                           (policy-request
                                            {:delegationEvidence
                                             {:policyIssuer data-owner-id
                                              :target       {:accessSubject client-id}
                                              :notBefore    (now)
                                              :notOnOrAfter (next-hour)
                                              :policySets
                                              [{:target   {:environment {:licenses ["0001"]}}
                                                :policies [{:rules [{:effect "Permit"}]
                                                            :target
                                                            {:resource    {:type        "Container-BOL-Events"
                                                                           :identifiers ["*"]
                                                                           :attributes  ["*"]}
                                                             :actions     ["read"]
                                                             :environment {:serviceProviders ["EU.EORI.CONNECTOR"]}}}]}]}}))))))
          (let [resp (client/exec (assoc backend-connector-request
                                         :method :get
                                         :path "/api/authorized"))]
            (is (= http-status/ok (:status resp))
                "status ok")))))))
