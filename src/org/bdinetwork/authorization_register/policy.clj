(ns org.bdinetwork.authorization-register.policy
  (:require [datascript.core :as ds])
  (:import java.util.UUID))

(defprotocol PolicyView
  (get-policies [x selector]
    "returns all policies matching selector"))

(defprotocol PolicyStore
  (add-policy! [x policy]
    "adds a new policy. returns policy new id")
  (delete-policy! [x id]
    "delete policy with id"))

(def schema
  {
   ;; policies are the root entities in the schema
   ;; the policy root has a "Permit" effect
   :policy/id                     {:db/unique :db.unique/identity}
   :policy/issuer                 {}
   :policy/max-delegation-depth   {}
   :policy/licenses               {:db/cardinality :db.cardinality/many}
   :target/access-subject         {}
   :target/actions                {:db/cardinality :db.cardinality/many}
   ;; delegation depth
   :resource/type                 {}
   :resource/identifiers          {:db/cardinality :db.cardinality/many}
   :resource/attributes           {:db/cardinality :db.cardinality/many}
   :environment/service-providers {:db/cardinality :db.cardinality/many}})

(defn delegation-mask->policy-selector
  "Convert an iSHARE delegation mask into a policy selector as defined
  by the PolicyView protocol"
  [mask]
  (reduce-kv
   (fn [selector k path]
     (if-let [v (get-in mask path)]
       (assoc selector k v)
       selector))
   {}
   ;; map of selector key -> delegation mask path
   {:policy/issuer                 ["policyIssuer"]
    :policy/max-delegation-depth   ["policySets" 0 "maxDelegationDepth"]
    :policy/licenses               ["policySets" 0  "target" "environment" "licenses"]
    :target/access-subject         ["target" "accessSubject"]
    :target/actions                ["policySets" 0 "policies" 0 "target" "actions"]
    :resource/type                 ["policySets" 0 "policies" 0 "target" "resource" "type"]
    :resource/identifiers          ["policySets" 0 "policies" 0 "target" "resource" "identifiers"]
    :resource/attributes           ["policySets" 0 "policies" 0 "target" "resource" "attributes"]
    :environment/service-providers ["policySets" 0 "policies" 0 "target" "environment" "serviceProviders"]}))

;; TODO this should merge restrictions from mask
(defn policy->delegation-evidence
  [policy]
  {"policyIssuer" (:policy/issuer policy)
   "target" {"accessSubject" (:target/access-subject policy)}
   "policySets" [{"maxDelegationDepth" (:policy/max-delegation-depth policy)
                  "target" {"environment" {"licenses" (:policy/licenses policy)}}
                  "policies" {"target" {"resource" {"type" (:resource/type policy)
                                                    "identifiers" (:resource/identifiers policy)
                                                    "attributes" (:resource/identifiers policy)}
                                        "actions" (:target/actions policy)
                                        "environment" {"serviceProviders" (:environment/service-providers policy)}}
                              "rules" [{"effect" "Allow"}]}}]})

(defn delegation-evidence
  [policy-view delegation-mask]
  (policy->delegation-evidence (first (get-policies policy-view (delegation-mask->policy-selector delegation-mask)))))

(defn delegate!
  [policy-store delegation]
  (add-policy! policy-store (delegation-mask->policy-selector delegation)))


;; https://dev.ishare.eu/reference/delegation-mask
;; 3https://dev.ishare.eu/reference/delegation-mask/policy-sets
;; https://framework.ishare.eu/detailed-descriptions/technical/structure-of-delegation-evidence
;; Data model
(comment


  (def delegation-evidence              ;1
    {:notBefore 1234
     :notOnOrAfter 5678
     :policyIssuer "..."
     :target {:accessSubject "..."}
     :policySets [{:maxDelegationDepth 4
                   :target {:environment {:licenses ["...."]}}
                   :policies [{:target {:resource ...
                                        :actions ..
                                        :environment ...}
                               ;; always only one rule with
                               ;; effect "Deny" or "Permit"


                               :rules [{:effect "...."}]}]}]})
  (def delegation-mask ;; delegationRequest
    {"policyIssuer" "EU.EORI.PRECIOUSG"
     "target" {"accessSubject" "EU.EORI.FLEXTRANS"}
     "policySets" [{"policies" [{"target" {"resource" {"identifiers" ["SOME.RESOURCE.ID"]}
                                           "actions" ["READ" "WRITE"]}}]}]
     "delegation_path" ["..."]
     "previous_steps" ["..."]})

  )
