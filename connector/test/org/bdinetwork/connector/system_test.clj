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
            [org.bdinetwork.test.system-helpers
             :refer [association-system
                     authorization-server-id
                     authorization-server-url
                     authorization-system
                     backend-connector-id
                     backend-connector-request
                     backend-connector-system
                     client-config
                     client-id
                     data-owner-config
                     data-owner-id
                     mk-oidc-access-token
                     noodlebar-request
                     noodlebar-system
                     oidc-system
                     policy-request
                     static-backend-system]]))

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
                   _backend-connector (backend-connector-system)
                   _noodbar-system (noodlebar-system)
                   _oidc (oidc-system)]
    (testing "ishare protocols"
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
                                           :path "/api/bdi/authenticated"))]
              (is (= http-status/ok (:status resp))
                  "status ok"))))

        (testing "using fake access token"
          (is (= http-status/unauthorized
                 (:status (client/exec (assoc backend-connector-request
                                              :method :get
                                              :path "/api/bdi/authenticated"
                                              :ishare/bearer-token "NONSENSE"))))
              "status unauthorized")))

      (testing "authorization"
        (testing "accessing authorzed backend"
          (testing "without correct policy"
            (let [resp (client/exec (assoc backend-connector-request
                                           :method :get
                                           :path "/api/bdi/authorized"))]
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
                                           :path "/api/bdi/authorized"))]
              (is (= http-status/ok (:status resp))
                  "status ok"))))))

    (testing "noodlebar"
      (testing "authentication checks"
        (testing "without an access token"
          (is (= http-status/unauthorized (-> noodlebar-request
                                              (assoc :method :get
                                                     :ishare/bearer-token nil
                                                     :path "/api/noodlebar/authenticated")
                                              client/exec
                                              :status))
              "unauthenticated"))

        (testing "with a valid access token"
          (is (= http-status/ok (-> noodlebar-request
                                    (assoc :method :get
                                           :ishare/bearer-token (mk-oidc-access-token {:aud backend-connector-id
                                                                                       :sub client-id})
                                           :path "/api/noodlebar/authenticated")
                                    client/exec
                                    :status))
              "authenticated")))

      (testing "authorization"
        (testing "accessing authorized backend"
          (is (= http-status/unauthorized (-> noodlebar-request
                                              (assoc :method :get
                                                     :ishare/bearer-token nil
                                                     :path "/api/noodlebar/authorized")
                                              client/exec
                                              :status))
              "unauthenticated")

          (is (= http-status/ok (-> noodlebar-request
                                    (assoc :method :get
                                           :ishare/bearer-token (mk-oidc-access-token {:aud backend-connector-id
                                                                                       :sub client-id})
                                           :path "/api/noodlebar/authorized")
                                    client/exec
                                    :status))
              "authenticated"))))))
