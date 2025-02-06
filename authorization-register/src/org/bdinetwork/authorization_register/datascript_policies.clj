;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.datascript-policies
  "This implements a PolicyView and PolicyStore using a DataScript database.

  The datascript connection can be extended to write to disk or some
  other storage backend.
  See https://github.com/tonsky/datascript/blob/master/docs/storage.md"
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [datascript.core :as ds]
            [org.bdinetwork.authorization-register.policies :refer [PolicyStore PolicyView schema] :as policies])
  (:import java.util.UUID))

(defn- bound-key-clause
  "Create eql clause for key k and value v.

  The matching policy ?e will either have key k with value v, or not
  have key k present (meaning match any value)."
  [k v]
  (list 'or
        ;; v is present for policy
        (if (= (get-in schema [k :db/cardinality]) :db.cardinality/many)
          ;; v must be a collection of values -- all values in the
          ;; selector must be present in the entity (but the entity
          ;; may have additional values)
          (if (< 1 (count v))
            ;; v has multiple values, must construct (and clauses..)
            ;; because this will be inside (or ...) clause
            (concat (list 'and)
                    (map (fn [sub-v]
                           ['?e k sub-v])
                         v))
            ;; v is collection with single value
            ['?e k (first v)])
          ;; v is a single value
          ['?e k v])
        ;; or k is not present; match any value
        (list 'not ['?e k])))

(defn- selector->where
  "Convert entity selector into eql where clauses.

  Entity selector is an key -> value map that may contain any key
  defined in `schema`. :db.cardinality/many keys must have a
  collection of values.

  Returns a set of clauses that match the given keys. Keys not present
  or nil will match elements that do not have that key (for any
  value)."
  [selector]
  {:pre [(seq selector)]}
  ;; for every attribute specified in selector
  (let [bound-keys   (filter #(some? (get selector %)) policies/query-attributes)
        unbound-keys (remove #(some? (get selector %)) policies/query-attributes)]
    (-> [['?e :policy/id]] ;; ensure we match policies
        ;; bound keys must be first
        (into (map #(bound-key-clause % (get selector %)) bound-keys))
        ;; (not [?e k]) removes entities with attribute k (for any
        ;; value) from the selection above.
        (into (map (fn [k] (list 'not ['?e k]))
                   unbound-keys)))))

(defn- selector->query
  [selector]
  (into '[:find (pull ?e [:*])
          :in $
          :where]
        (selector->where selector)))

(defrecord DataScriptPolicies [conn]
  PolicyView
  (get-policies [_ selector]
    (first (ds/q (selector->query selector)
                 (ds/db conn))))

  PolicyStore
  (add-policy! [_ policy]
    (let [id (UUID/randomUUID)]
      (log/debug "Adding policy " id policy)
      (ds/transact! conn [(assoc (select-keys policy (keys schema)) :policy/id id)])
      id))
  (delete-policy! [_ id]
    (log/debug "Deleting policy" id)
    (ds/transact! conn [[:db/retractEntity [:policy/id id]]])))

(defn non-empty-dir?
  [dir]
  (let [f (io/file dir)]
    (and (.exists f)
         (.isDirectory f)
         (boolean (seq (.listFiles f))))))

(defn file-conn
  "Given directory, returns a connection.

  If the dir is not empty, restores the connection from dir.

  Otherwise create a new connection, creating the dir if needed."
  [dir]
  (if (non-empty-dir? dir)
    (ds/restore-conn (ds/file-storage dir))
    (ds/create-conn schema {:storage (ds/file-storage dir)})))

(defn file-backed-policies
  "Create a policies DB that's stored on disk."
  [f]
  (->DataScriptPolicies (file-conn f)))

(defn in-memory-policies
  "Create an empty in-memory policy store."
  []
  (->DataScriptPolicies (ds/create-conn schema)))


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
