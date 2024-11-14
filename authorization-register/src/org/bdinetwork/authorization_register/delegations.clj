;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.delegations
  "Managing and querying authorizations with iSHARE style delegations.

  A delegation-evidence describes an authorization for a particular
  access-subject and target.

  A delegation-mask describes a request for some delegation-evidence.

  The authorizations are retrieved from a policy store. See
  `org.bdinetwork.authorization-register.policies`. New policies are
  created by converting a delegation-evidence into a policy."
  (:require [org.bdinetwork.authorization-register.policies :as policies]))

(defn delegation-evidence->policy
  "Convert an iSHARE delegation-evidence into a policy."
  [delegation-evidence]
  (assert (= "Permit" (get-in delegation-evidence ["policySets" 0 "policies" 0 "rules" 0 "effect"]))
          "Cannot convert 'Deny' effects into policies")
  (reduce-kv
   (fn [policy k path]
     (if-let [v (get-in delegation-evidence path)]
       (assoc policy k v)
       policy))
   {}
   ;; map of selector key -> delegation mask path
   {:policy/issuer                 ["policyIssuer"]
    :policy/max-delegation-depth   ["policySets" 0 "maxDelegationDepth"]
    :target/access-subject         ["target" "accessSubject"]
    :policy/licenses               ["policySets" 0 "target" "environment" "licenses"]
    :target/actions                ["policySets" 0 "policies" 0 "target" "actions"]
    :resource/type                 ["policySets" 0 "policies" 0 "target" "resource" "type"]
    :resource/identifiers          ["policySets" 0 "policies" 0 "target" "resource" "identifiers"]
    :resource/attributes           ["policySets" 0 "policies" 0 "target" "resource" "attributes"]
    :environment/service-providers ["policySets" 0 "policies" 0 "target" "environment" "serviceProviders"]}))

(defn delegation-mask->policy-selector
  "Convert an iSHARE delegation mask into a policy selector as defined by the PolicyView protocol."
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
    :target/access-subject         ["target" "accessSubject"]
    :target/actions                ["policySets" 0 "policies" 0 "target" "actions"]
    :resource/type                 ["policySets" 0 "policies" 0 "target" "resource" "type"]
    :resource/identifiers          ["policySets" 0 "policies" 0 "target" "resource" "identifiers"]
    :resource/attributes           ["policySets" 0 "policies" 0 "target" "resource" "attributes"]
    :environment/service-providers ["policySets" 0 "policies" 0 "target" "environment" "serviceProviders"]}))

(defn policy->delegation-evidence
  [{:resource/keys    [type identifiers attributes]
    :environment/keys [service-providers]
    :target/keys      [actions] :as policy}
   permit?]
  {"policyIssuer" (:policy/issuer policy)
   "target"       {"accessSubject" (:target/access-subject policy)}
   "policySets"   [{"maxDelegationDepth" (:policy/max-delegation-depth policy)
                    "target"             {"environment" {"licenses" (:policy/licenses policy)}}
                    "policies"           [{"target" (cond-> {"resource" (cond-> {}
                                                                          (some? type)
                                                                          (assoc "type" type)
                                                                          (some? identifiers)
                                                                          (assoc "identifiers" identifiers)
                                                                          (some? attributes)
                                                                          (assoc "attributes" attributes))}
                                                      (seq actions)
                                                      (assoc "actions" actions)
                                                      (seq service-providers)
                                                      (assoc-in ["environment" "serviceProviders"] service-providers))
                                           "rules"  [{"effect" (if permit? "Permit" "Deny")}]}]}]})

(defn delegation-evidence
  [policy-view delegation-mask]
  {:pre [delegation-mask]}
  (let [selector (delegation-mask->policy-selector delegation-mask)
        policy (first (policies/get-policies policy-view selector))]
    (-> policy
        ;; Merge the selector into the policy since the returned
        ;; delegation evidence should be restricted by the selector. For
        ;; example: if the delegation mask requests authorization for a
        ;; particular resource id, but the backing policy grants
        ;; authorization for all resource of some type, we want the
        ;; resulting delegation evidence to be restricted to the requested
        ;; resource.
        (merge selector)
        ;; if no policy is found, the delegation evidence must be
        ;; negative (have a "Deny" effect).
        (policy->delegation-evidence (some? policy)))))

(defn delegate!
  [policy-store delegation]
  (policies/add-policy! policy-store (delegation-evidence->policy delegation)))

(defn delete-delegation!
  [policy-store id]
  (policies/delete-policy! policy-store id))

;; https://dev.ishare.eu/reference/delegation-mask
;; 3https://dev.ishare.eu/reference/delegation-mask/policy-sets
;; https://framework.ishare.eu/detailed-descriptions/technical/structure-of-delegation-evidence
;; Data model
