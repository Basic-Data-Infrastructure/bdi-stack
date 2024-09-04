(ns org.bdinetwork.authorization-register.in-memory-policies-test
  (:require  [clojure.test :refer [deftest is]]
             [org.bdinetwork.authorization-register.policy :as policy]
             [org.bdinetwork.authorization-register.in-memory-policies :refer [in-memory-policies]]))

(def delegation-mask ;; delegationRequest
  {"policyIssuer"    "EU.EORI.PRECIOUSG"
   "target"          {"accessSubject" "EU.EORI.FLEXTRANS"}
   "policySets"      [{"policies" [{"target" {"resource" {"identifiers" ["SOME.RESOURCE.ID"]}
                                              "actions"  ["READ" "WRITE"]}}]}]
   "delegation_path" ["..."]
   "previous_steps"  ["..."]})

(deftest basic
  (let [p         (in-memory-policies)
        policy-id (policy/delegate! p delegation-mask)]
    (is (uuid? policy-id)
        "can insert delegation")

    (is (= [{:db/id                 1
             :policy/id             policy-id
             :policy/issuer         "EU.EORI.PRECIOUSG"
             :resource/identifiers  ["SOME.RESOURCE.ID"]
             :target/access-subject "EU.EORI.FLEXTRANS"
             :target/actions        ["READ" "WRITE"]}]
           (policy/get-policies p {:policy/issuer "EU.EORI.PRECIOUSG"}))
        "Can fetch policies")

    (is (= {"policyIssuer" "EU.EORI.PRECIOUSG",
            "target"       {"accessSubject" "EU.EORI.FLEXTRANS"}
            "policySets"
            [{"maxDelegationDepth" "TODO"
              "target"             {"environment" {"licenses" "TODO"}}
              "policies"
              {"target"
               {"resource"
                {"type"        nil,
                 "identifiers" ["SOME.RESOURCE.ID"]
                 "attributes"  ["SOME.RESOURCE.ID"]}
                "actions"     ["READ" "WRITE"]
                "environment" {"serviceProviders" nil}}
               "rules" [{"effect" "Allow"}]}}]}
           (policy/delegation-evidence p delegation-mask)))
    )
  )
