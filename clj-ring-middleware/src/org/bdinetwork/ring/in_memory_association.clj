;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.in-memory-association
  (:require [org.bdinetwork.ring.ishare-validator :refer [parse-yaml validate]]
            [org.bdinetwork.ring.authentication.x5c :refer [fingerprint subject-name]]
            [buddy.core.codecs :as codecs]
            [buddy.core.certificates :as certificates]
            [org.bdinetwork.ring.association :refer [Association]]))

(defrecord InMemoryAssociation [source]
  Association
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
            parties)))
  (trusted-list [_]
    (get source "trusted_list")))

(defn in-memory-association
  "Create a new in-memory Assocation from source data"
  [source]
  {:pre [(map? source)]}
  (when-let [issues (validate (get source "parties")
                              ["components" "schemas" "PartiesInfo" "properties" "data"])]
    (throw (ex-info "Invalid party in data source" {:issues issues})))
  (->InMemoryAssociation source))

(defn- read-certificate-info
  [path]
  (let [c (certificates/certificate path)]
    {"x5t#s256"         (fingerprint c)
     "x5c"              (codecs/bytes->b64-str (.getEncoded c))
     "subject_name"     (subject-name c)
     "enabled_from"     (str (.toInstant (.getNotBefore c)))
     "certificate_type" "Unknown"}))

(defn- parse-certificate
  [cert]
  (if (string? cert)
    (read-certificate-info cert)
    cert))

(defn- parse-party
  "Parse party info; reads the party's certificates from source pems."
  [party]
  (update party "certificates"
          #(map parse-certificate %)))

(defn- parse-ca
  "Parse CA info; reads from file is ca-info is a string"
  [ca-info]
  (if (string? ca-info)
    (let [c (certificates/certificate ca-info)]
      {"subject"                 (subject-name c)
       "certificate_fingerprint" (fingerprint c)
       "validity"                "Valid"
       "status"                  "Granted"})
    ca-info))

(defn read-source
  "Read source data from yaml file at `path`."
  [path]
  (-> (parse-yaml path)
      (update "parties" #(map parse-party %))
      (update "trusted_list" #(map parse-ca %))))
