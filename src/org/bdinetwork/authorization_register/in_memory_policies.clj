(ns org.bdinetwork.authorization-register.in-memory-policies
  (:require
   [datascript.core :as ds]
   [org.bdinetwork.authorization-register.policy
    :refer
    [PolicyStore PolicyView schema]])
  (:import
   (java.util UUID)))

(defn selector->where
  "Convert entity selector into eql where clauses.

  Entity selector is an key -> value map that may contain any key
  defined in `schema`. :db.cardinality/many keys must have a
  collection of values.

  Returns a set of where clauses that match the given keys. Keys not
  present are ignored, so the where clauses will match entities with
  any (or no) value for those keys."
  [selector]
  (reduce-kv
   (fn [where k v]
     (if (= (get-in schema [k :db/cardinality])
            :db.cardinality/many)
       ;; v must be a collection of values
       (reduce (fn [where v]
                 (conj where ['?e k v]))
               where
               v)
       ;; v is a single value
       (conj where ['?e k v])))
   []
   (select-keys selector (keys schema))))

(defn selector->query
  [selector]
  (into '[:find (pull ?e [:*])
          :in $
          :where]
        (selector->where selector)))

(defrecord InMemoryPolicies [conn]
  PolicyView
  (get-policies [_ selector]
    (first (ds/q (selector->query selector)
                 (ds/db conn))))

  PolicyStore
  (add-policy! [_ policy]
    (let [id (UUID/randomUUID)]
      (ds/transact! conn [(assoc (select-keys policy (keys schema)) :policy/id id)])
      id))
  (delete-policy! [_ id]
    (ds/transact! conn [[:db/retractEntity [:policy/id id]]])))

(defn in-memory-policies
  []
  (->InMemoryPolicies (ds/create-conn schema)))



;; https://dev.ishare.eu/reference/delegation-mask
;; https://dev.ishare.eu/reference/delegation-mask/policy-sets
;; https://framework.ishare.eu/detailed-descriptions/technical/structure-of-delegation-evidence
;; Data model
(comment

  (def policy
    {
     :target {:resource    {:type        "type"
                            :identifiers ["..."]
                            :attributes  ["..."]}
              :actions     ["..."]
              :environment {:serviceProviders ["..."]}}
     :rules  [ ;; first rule is default and MUST be "Permit"
              {:effect "Permit"}
              ;; other rules MUST be "Deny"
              ;; these additional rules are NEVER in delegation evidence
              ;; or in delegation mask
              {:effect "Deny"
               :target {:resource { ;; at least one of type, attributes or identifiers must be present
                                   :type        "type"
                                   ;; values for identifiers and attributes
                                   ;; should be URNs
                                   :identifiers ["..."]
                                   :attributes  ["..."]}
                        :actions ["..."]}}]})

  (def delegation-evidence
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


  )
