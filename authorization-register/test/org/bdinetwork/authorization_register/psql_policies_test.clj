;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.psql-policies-test
  (:require [clojure.test :refer [deftest testing]]
            [environ.core :refer [env]]
            [next.jdbc :as jdbc]
            [nl.jomco.envopts :refer [opts!]]
            [org.bdinetwork.authorization-register.policies-test :refer [test-policy-store-impl]]
            [org.bdinetwork.authorization-register.psql-policies :refer [init-policies]]))

(def test-opts-specs
  {"AR_TEST_DB_NAME"     ["Postgres database name for testing Authorisation Register (should be empty)" :str :default "bdi_test_policies" :in [:dbname]]
   "AR_TEST_DB_USER"     ["Postgres user for AR tests" :str :in [:user]]
   "AR_TEST_DB_PASSWORD" ["Postgres password for AR tests" :str :in [:password]]
   "AR_TEST_DB_HOST"     ["Postgres host for AR tests" :str :default "localhost" :in [:host]]
   "AR_TEST_DB_PORT"     ["Postgres host for AR tests" :int :default 5432 :in [:port]]})

(defn dbspec
  []
  (prn {:opts (nl.jomco.envopts/opts env test-opts-specs)})
  (merge {:dbtype "postgres"}
         (opts! env test-opts-specs)))

(defn psql-policies
  []
  (let [policies (init-policies (dbspec))]
    (jdbc/execute-one! (dbspec) ["delete from policy"])
    policies))

(deftest psql-policies-test
  (testing "psql policy store"
    (test-policy-store-impl psql-policies)))
