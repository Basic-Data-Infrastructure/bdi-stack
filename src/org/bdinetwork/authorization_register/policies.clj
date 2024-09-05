(ns org.bdinetwork.authorization-register.policies)

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

