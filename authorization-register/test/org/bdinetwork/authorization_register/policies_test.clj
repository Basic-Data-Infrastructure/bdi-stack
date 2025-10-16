;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.policies-test
  (:require [clojure.test :refer [testing is]]
            [org.bdinetwork.authorization-register.policies :as policies]))

(defn test-policy-store-impl
  "Run tests against some PolicyStore implementation.

  This function should be called from a `deftest` that sets up
  `mk-store`, which is a function that returns an empty PolicyStore."
  [mk-store]
  (testing "simple storage and retrieval"
    (let [store (mk-store)
          policy #:policy {:access-subject "sub" :issuer "iss" :actions ["READ" "WRITE"]}
          id (policies/add-policy! store policy)
          with-id (assoc policy :policy/id id)]
      (policies/add-policy! store #:policy {:access-subject "dummy" :issuer "dummy" :actions ["READ" "WRITE"]})
      (is (some? id)
          "add-policy returns the generated id of the new policy")
      (is (= [with-id]
             (policies/get-policies store with-id))
          "can retrieve policy by id")
      (policies/delete-policy! store id)
      (is (nil? (policies/get-policies store with-id))
          "policy is deleted")))

  (testing "queries"
    (let [store (mk-store)
          policy #:policy {:access-subject "sub" :issuer "iss" :actions ["READ" "WRITE"]}
          id (policies/add-policy! store policy)
          with-id (assoc policy :policy/id id)]
      (is (= [with-id]
             ;; NOTE: get-policies should return the policy as it was
             ;; stored, which means that the returned policy can be
             ;; more permissive than the given selector (i.e. in this
             ;; case the policy includes a "WRITE" action that is not
             ;; present in the selector)
             (policies/get-policies store (assoc policy :policy/actions ["READ"])))
          "can retrieve policy matching one of :cardinality/many fields"))))
