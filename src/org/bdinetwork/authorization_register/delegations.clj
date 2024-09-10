;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.delegations
  (:require [org.bdinetwork.authorization-register.policies :as policies]))

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
  (policy->delegation-evidence (first (policies/get-policies policy-view (delegation-mask->policy-selector delegation-mask)))))

(defn delegate!
  [policy-store delegation]
  (policies/add-policy! policy-store (delegation-mask->policy-selector delegation)))

(defn delete-delegation!
  [policy-store id]
  (policies/delete-policy! policy-store id))

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
