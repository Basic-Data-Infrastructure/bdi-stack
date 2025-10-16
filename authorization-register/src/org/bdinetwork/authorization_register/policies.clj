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

(defprotocol PolicyStore
  "Defines methods for storing and retrieving policies.

  Policies describe delegated authorizations: a policy issuer allows
  an access-subject to perform actions on a resource provided by
  some resource-providers.

  Policies are maps of attributes from `schema` to values.  If the
  attribute is `many-key?`, the policy has a possibly empty collection
  of values for that attribute.
  
  Semantically, a missing or `nil` value means \"allow any value\": a
  policy with a missing `:policy/actions` attribute allows the
  `:policy/access-subject` to perform any action."
  (get-policies [store selector]
    "Return all policies matching `selector`.

    `selector` is a map of attributes from `query-attributes` to
    values.

    If any attribute is missing or nil in the selector it must be
    missing or nil in the returned policies.

    If an attribute is present and non-nil in the selector, it must
    either be present in the policies with the same value(s), or the
    attribute must be missing or nil in the policies.

    In the case of :db.cardinality/many attributes, the policy may have
    additional values for the given attribute.")
  (add-policy! [store policy]
    "Add a new policy, return the generated policy id.")
  (delete-policy! [store id]
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

(defn many-key?
  "Return true if `attribute` is a may-value key in the policy schema."
  [attr]
  (= :db.cardinality/many (get-in schema [attr :db/cardinality])))


(def query-attributes
  "These attributes can be used in the selector for `get-policies`."
  [:policy/issuer
   :policy/access-subject
   :policy/actions
   :policy/resource-type
   :policy/resource-identifiers
   :policy/resource-attributes
   :policy/service-providers])
