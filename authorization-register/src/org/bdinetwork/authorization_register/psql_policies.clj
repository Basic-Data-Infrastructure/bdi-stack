;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.psql-policies
  "Implement a PolicyView and PolicyStore using a PostgreSQL database."
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]
            [next.jdbc.sql.builder :as builder]
            [org.bdinetwork.authorization-register.policies :refer [PolicyStore] :as policies])
  (:import java.sql.Array
           java.util.UUID))

(extend-protocol rs/ReadableColumn
  Array
  (read-column-by-label [^Array v _]    (vec (.getArray v)))
  (read-column-by-index [^Array v _ _]  (vec (.getArray v))))

(def ^:private empty-array
  (make-array Object 0))

(defn- ->arr
  [x]
  (if (vector? x)
    (if (seq x)
      (into-array (class (first x))
                  x)
      empty-array)
    x))

;; convert vector values in m into arrays
(defn- ->record
  [m]
  (update-vals m ->arr))


(defn- many-keys-seq
  [key-map]
  (mapcat (fn [[k v :as entry]]
            (if (policies/many-key? k)
              (mapv (fn [v]
                      [k v])
                    v)
              [entry]))
          key-map))

(defn- selector->sql-clause
  "Build SQL clause from selector.

  Implements the logic as specified in
  `org.bdinetwork.authorization-register.psql-policies/get-policies`

  Takes into account many-valued keys in selector (these map to PSQL
  array ANY matches).

  Applies any `:column-fn` supplied in the options.

  Based off next.jdbc.sql.builder/by-keys"
  [selector opts]
  (let [entity-fn      (:column-fn opts identity)
        [where params] (reduce (fn [[conds params] [k v]]
                                    (if (some? v)
                                      (let [e (entity-fn (#'builder/safe-name k))]
                                        (if (policies/many-key? k)
                                          [(conj conds (str "( ? = ANY (" e ") OR " e " IS NULL)")) (conj params v)]
                                          [(conj conds (str "(" e " = ? OR " e " IS NULL)")) (conj params v)]))
                                      [])) [[] []]
                                  (many-keys-seq selector))]
    (into [(string/join " AND " where)]
          params)))

(defn- selector->query
  "Convert policy selector into Psql SELECT query."
  [selector]
  (builder/for-query :policy
                     (selector->sql-clause selector jdbc/snake-kebab-opts)
                     jdbc/snake-kebab-opts))

;; TODO: it would be nicer to have this as part of the rs/builder function.
;; See jdbc/snake-kebab-opts
(defn- strip-nils
  [policy]
  (persistent!
   (reduce-kv (fn [m k v]
                (if (some? v)
                  (assoc! m k v)
                  m))
              (transient (empty policy))
              policy)))

(defrecord PsqlPolicies [conn]
  PolicyStore
  (get-policies [_ selector]
    (->> (sql/query conn (selector->query selector) jdbc/snake-kebab-opts)
         (map strip-nils)
         (seq)))
  (add-policy! [_ policy]
    (let [id (UUID/randomUUID)] ;; TODO: use v7 uuids for better index performance
      (log/debug "Adding policy " id policy)
      (sql/insert! conn :policy (-> policy
                                    (assoc :policies/id id)
                                    ->record)
                   jdbc/snake-kebab-opts)
      id))
  (delete-policy! [_ id]
    (log/debug "Deleting policy" id)
    (sql/delete! conn :policy {:id id})))

(defn migratus-config
  [dbspec]
  {:store         :database
   :migration-dir "migrations"
   :db            dbspec})

(defn init-policies
  [dbspec]
  (migratus/migrate (migratus-config dbspec))
  (->PsqlPolicies (jdbc/get-connection dbspec)))

(defn psql-policies
  [dbspec]
  (init-policies dbspec))

(comment
  (def dbspec {:dbtype "postgres" :dbname "bdi_policies" :user "bdi_ar" :password "bdi_ar"}))
