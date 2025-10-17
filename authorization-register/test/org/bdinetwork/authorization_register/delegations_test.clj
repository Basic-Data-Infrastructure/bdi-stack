;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.delegations-test
  (:require [clojure.test :refer [deftest is]]
            [org.bdinetwork.authorization-register.datascript-policies :refer [in-memory-policies]]
            [org.bdinetwork.authorization-register.delegations :as delegations]
            [org.bdinetwork.authorization-register.policies :as policies]))

(def delegation-mask ;; delegationRequest
  {"policyIssuer"    "EU.EORI.PRECIOUSG"
   "target"          {"accessSubject" "EU.EORI.FLEXTRANS"}
   "policySets"      [{"policies" [{"target" {"resource" {"identifiers" ["SOME.RESOURCE.ID"]}
                                              "actions"  ["WRITE"]}}]}]
   "delegation_path" ["..."]
   "previous_steps"  ["..."]})

(def delegation-evidence
  {"policyIssuer" "EU.EORI.PRECIOUSG"
   "notBefore"    1
   "notOnOrAfter" 2
   "target"       {"accessSubject" "EU.EORI.FLEXTRANS"}
   "policySets"   [{"maxDelegationDepth" 4
                    "target"             {"environment" {"licenses" ["AGPL"]}}
                    "policies"           [{"target" {"resource" {"identifiers" ["SOME.RESOURCE.ID"]}
                                                     "actions"  ["READ" "WRITE"]}
                                           "rules"  [{"effect" "Permit"}]}]}]
   })

(deftest mapping
  (is (= {:policy/issuer         "EU.EORI.PRECIOUSG"
          :policy/resource-identifiers  ["SOME.RESOURCE.ID"]
          :policy/access-subject "EU.EORI.FLEXTRANS"
          :policy/actions        ["READ"]}
         (delegations/delegation-mask->policy-selector
          {"policyIssuer"    "EU.EORI.PRECIOUSG"
           "target"          {"accessSubject" "EU.EORI.FLEXTRANS"}
           "policySets"      [{"policies" [{"target" {"resource" {"identifiers" ["SOME.RESOURCE.ID"]}
                                                      "actions"  ["READ"]}}]}]
           "delegation_path" ["..."]
           "previous_steps"  ["..."]})))

  (is (= {:policy/issuer               "EU.EORI.PRECIOUSG"
          :policy/not-before           1
          :policy/not-on-or-after      2
          :policy/resource-identifiers        ["SOME.RESOURCE.ID"]
          :policy/access-subject       "EU.EORI.FLEXTRANS"
          :policy/licenses             ["AGPL"]
          :policy/max-delegation-depth 4
          :policy/actions              ["READ" "WRITE"]}
         (delegations/delegation-evidence->policy
          delegation-evidence))))

(deftest basic
  (let [p         (in-memory-policies)
        policy-id (delegations/delegate! p delegation-evidence)]
    (is (uuid? policy-id)
        "can insert delegation")

    (is (= [{:policy/id                   policy-id
             :policy/issuer               "EU.EORI.PRECIOUSG"
             :policy/max-delegation-depth 4
             :policy/licenses             ["AGPL"]
             :policy/not-before           1
             :policy/not-on-or-after      2
             :policy/resource-identifiers        ["SOME.RESOURCE.ID"]
             :policy/access-subject       "EU.EORI.FLEXTRANS"
             :policy/actions              ["READ" "WRITE"]}]
           (policies/get-policies p {:policy/issuer         "EU.EORI.PRECIOUSG"
                                     :policy/resource-identifiers  ["SOME.RESOURCE.ID"]
                                     :policy/access-subject "EU.EORI.FLEXTRANS"
                                     :policy/actions        ["READ"]}))
        "Can fetch policies")

    (is (= {"policyIssuer" "EU.EORI.PRECIOUSG",
            "target"       {"accessSubject" "EU.EORI.FLEXTRANS"}
            "notBefore"    1
            "notOnOrAfter" 2
            "policySets"
            [{"maxDelegationDepth" 4
              "target"             {"environment" {"licenses" ["AGPL"]}}
              "policies"
              [{"target"
                {"resource"
                 {"identifiers" ["SOME.RESOURCE.ID"]}
                 "actions" ["WRITE"]}
                "rules" [{"effect" "Permit"}]}]}]}
           (delegations/delegation-evidence p delegation-mask)))))
