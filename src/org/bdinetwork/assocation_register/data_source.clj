;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.assocation-register.data-source
  (:require [clojure.string :as string]
            [org.bdinetwork.assocation-register.ishare-validator :refer [parse-yaml validate]])
  (:import (java.time Instant)))

(defprotocol DataSourceProtocol
  "This is the protocal describing access to the data source."
  (party [source eori]
    "Returns the first party with a given EORI."))



(deftype InMemoryDataSource [source]
  DataSourceProtocol
  ;; Right now we track the OpenAPI schema closely in these interfaces,
  ;; both for return shapes and parameters. Should we keep this up, or
  ;; implement internal interfaces and data model differently?
  ;;
  ;; If we want to use a different model, is there an existing information
  ;; model we could use? Preferably with a standardized translation?
  ;;
  ;; Related: use keywords instead of strings in internal representation?
  ;; namespaced keys? Use time objects instead of strings?
  (party [_ party-id]
    (let [{:strs [parties]} source]
      (some #(when (= party-id (get % "party_id"))
               %)
            parties))))

(defn yaml-in-memory-data-source-factory
  "Read all source data from YAML file `in` for in memory store."
  [in]
  (let [source (parse-yaml in)]
    (when-let [issues (validate (get source "parties")
                                ["components" "schemas" "PartiesInfo" "properties" "data"])]
      (throw (ex-info "Invalid party in data source" {:issues issues})))
    (InMemoryDataSource. source)))

(comment
  (party (yaml-in-memory-data-source-factory "test/test-config.yml") "EU.EORI.CLIENT"))
