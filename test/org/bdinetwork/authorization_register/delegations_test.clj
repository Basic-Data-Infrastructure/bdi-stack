;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.delegations-test
  (:require [clojure.test :refer [deftest is]]
            [org.bdinetwork.authorization-register.delegations :as delegations]
            [org.bdinetwork.authorization-register.in-memory-policies :refer [in-memory-policies]]
            [org.bdinetwork.authorization-register.policies :as policies]))

(def delegation-mask ;; delegationRequest
  {"policyIssuer"    "EU.EORI.PRECIOUSG"
   "target"          {"accessSubject" "EU.EORI.FLEXTRANS"}
   "policySets"      [{"policies" [{"target" {"resource" {"identifiers" ["SOME.RESOURCE.ID"]}
                                              "actions"  ["READ" "WRITE"]}}]}]
   "delegation_path" ["..."]
   "previous_steps"  ["..."]})

(def delegation
  {"policyIssuer"    "EU.EORI.PRECIOUSG"
   "target"          {"accessSubject" "EU.EORI.FLEXTRANS"}
   "policySets"      [{"maxDelegationDepth" 4
                       "target" {"environment" {"licenses" "AGPL"}}
                       "policies" [{"target" {"resource" {"identifiers" ["SOME.RESOURCE.ID"]}
                                              "actions"  ["READ" "WRITE"]}}]}]})

(deftest basic
  (let [p         (in-memory-policies)
        policy-id (delegations/delegate! p delegation)]
    (is (uuid? policy-id)
        "can insert delegation")

    (is (= [{:db/id                       1
             :policy/id                   policy-id
             :policy/issuer               "EU.EORI.PRECIOUSG"
             :policy/max-delegation-depth 4
             :policy/licenses             ["AGPL"]
             :resource/identifiers        ["SOME.RESOURCE.ID"]
             :target/access-subject       "EU.EORI.FLEXTRANS"
             :target/actions              ["READ" "WRITE"]}]
           (policies/get-policies p {:policy/issuer "EU.EORI.PRECIOUSG"}))
        "Can fetch policies")

    (is (= {"policyIssuer" "EU.EORI.PRECIOUSG",
            "target"       {"accessSubject" "EU.EORI.FLEXTRANS"}
            "policySets"
            [{"maxDelegationDepth" 4
              "target"             {"environment" {"licenses" ["AGPL"]}}
              "policies"
              {"target"
               {"resource"
                {"type"        nil,
                 "identifiers" ["SOME.RESOURCE.ID"]
                 "attributes"  ["SOME.RESOURCE.ID"]}
                "actions"     ["READ" "WRITE"]
                "environment" {"serviceProviders" nil}}
               "rules" [{"effect" "Allow"}]}}]}
           (delegations/delegation-evidence p delegation-mask)))))
