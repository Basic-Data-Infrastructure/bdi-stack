;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.policies
  "Defines the protocol for storing and retrieving policies.

  The `org.bdinetwork.authorization-register.delegations` uses this
  protocol to provide delegation evidence.")

(defprotocol PolicyView
  (get-policies [x selector]
    "Return all policies matching selector."))

(defprotocol PolicyStore
  (add-policy! [x policy]
    "Add a new policy, return policy new id.")
  (delete-policy! [x id]
    "Delete policy with id."))

(def schema
  {
   ;; policies are the root entities in the schema
   ;; the policy root has a "Permit" effect
   :policy/id                   {:db/unique :db.unique/identity}
   :policy/issuer               {}
   :policy/not-before           {}
   :policy/not-on-or-after      {}
   :policy/max-delegation-depth {}
   :policy/licenses             {:db/cardinality :db.cardinality/many}
   :policy/access-subject       {}
   :policy/actions              {:db/cardinality :db.cardinality/many}
   ;; delegation depth
   :policy/resource-type        {}
   :policy/resource-identifiers {:db/cardinality :db.cardinality/many}
   :policy/resource-attributes  {:db/cardinality :db.cardinality/many}
   :policy/service-providers    {:db/cardinality :db.cardinality/many}})

(def query-attributes
  "These attributes can be queried on using `get-policies`.

  If any attribute is misssing in the query it must also be missing in
  the policy.

  If an attribute is present in the selector, it must either be
  present in the policy with the same value(s), or the attribute must
  be missing from the policy.

  In the case of :db.cardinality/many attributes, the policy may have
  additional values for the given attribute."
  [:policy/issuer
   :policy/access-subject
   :policy/actions
   :policy/resource-type
   :policy/resource-identifiers
   :policy/resource-attributes
   :policy/service-providers])
