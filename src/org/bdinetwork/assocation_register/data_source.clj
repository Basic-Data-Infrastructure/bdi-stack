;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.assocation-register.data-source
  (:require [org.bdinetwork.assocation-register.ishare-validator :refer [parse-yaml validate]]
            [clojure.string :as string])
  (:import java.time.Instant))

(defn read-data-source
  [in]
  (let [source (parse-yaml in)]
    (when-let [issues (validate (get source "parties")
                                ["components" "schemas" "PartiesInfo" "properties" "data"])]
      (throw (ex-info "Invalid party in data source" {:issues issues})))
    source))

(defn- wildcard-pred
  [prop val]
  (if-let [[_ start end] (re-find #"(.*)\*(.*)" val)]
    (fn [party]
      (let [v (get party prop)]
        (and (string/starts-with? v start)
             (string/ends-with? v end))))
    #(= val (get % prop))))

(def certified-roles
  ;; According to the iShare specs, iShareSatellite is NOT a certified
  ;; role.
  ;;
  ;; https://framework.ishare.eu/main-aspects-of-the-ishare-trust-framework/framework-and-roles
  #{"IdentityProvider" "IdentityBroker" "AuthorisationRegistry"})

(defn- select-role
  [{:strs [roles] :as _party} role]
  (first (filter #(= role (get % "role")) roles)))

(defn- parse-time-stamp
  [ts]
  (Instant/parse ts))

;; fixed size in iSHARE spec
(def page-size 10)

;; Page 1 is the first page
(defn paginate
  [page k coll]
  {:pre [(<= 1 page)]}
  (let [c (count coll)]
    {k {"data"        (->> coll
                           (drop (* page-size (dec page)))
                           (take page-size))
        "total_count" c
        "page_count"  (inc (int (/ (dec c) page-size)))}}))

(defn- split-tags
  [s]
  (if (or (nil? s)
          (string/blank? s))
    #{}
    (into #{} (-> s
                  (string/trim)
                  (string/split #"\s+")))))

(defn- has-tags?
  [party tags]
  (let [party-tags (-> party
                       (get-in ["additional_info" "tags"])
                       (split-tags))]
    (every? #(contains? party-tags %) (split-tags tags))))

;; This is a pretty ugly interface:
;;
;;   - parameters are inconsistent in camelCase and snake_case
;;
;;   - returned attribute names are inconsistent with parameter names
;;
;;   - some of the queries are unnessary (active_only vs
;;     adherenceStatus)
;;
;;   - the collection queries may have unexpected results (loA,
;;     certifiedOnly)
;;
;; Right now we track the OpenAPI schema closely in these interfaces,
;; both for return shapes and parameters. Should we keep this up, or
;; implement internal interfaces and data model differently?
;;
;; If we want to use a different model, is there an existing information
;; model we could use? Preferably with a standardized translation?
;;
;; Related: use keywords instead of strings in internal representation?
;; namespaced keys? Use time objects instead of strings?


;; TODO: define Protocol for these
;; TODO: Remove parties call

(defn parties
  [{:strs [parties] :as _source} {:strs [active_only
                                         adherenceEnddate
                                         adherenceStartdate
                                         adherenceStatus
                                         authorizationRegistryID
                                         authorizationRegistryName
                                         certificate_subject_name
                                         certified_only
                                         companyEmail
                                         companyPhone
                                         compliancyVerified
                                         countriesOfOperation
                                         dataSpaceID
                                         dataSpaceTitle
                                         date_time
                                         eori
                                         framework
                                         legalAdherence
                                         loA
                                         name
                                         page
                                         publiclyPublishable
                                         registarSatelliteID
                                         role
                                         sectorIndustry
                                         subjectName
                                         tags
                                         webSiteUrl]}]
  (cond->> parties
    (some? active_only)
    (filter #((if active_only = not=) "Active" (get-in % ["adherence" "status"])))

    (some? adherenceEnddate)
    (filter #(= adherenceEnddate (get-in % ["adherence" "end_date"])))

    (some? adherenceStartdate)
    (filter #(= adherenceStartdate (get-in % ["adherence" "start_date"])))

    ;; FIXME: take start and end date into account?
    (some? adherenceStatus)
    (filter #(= adherenceStatus (get-in % ["adherence" "status"])))

    (some? authorizationRegistryID)
    (filter (fn [{:strs [auth_registries]}]
              (some #(= authorizationRegistryID (get % "id"))
                    auth_registries)))

    (some? authorizationRegistryName)
    (filter (fn [{:strs [auth_registries]}]
              (some #(= authorizationRegistryName (get % "name"))
                    auth_registries)))

    (some? certificate_subject_name)
    (filter (fn [{:strs [certificates]}]
              (some #(= certificate_subject_name (get % "subject_name"))
                    certificates)))

    ;; certified_only = false does not filter
    (true? certified_only)
    (filter (fn [{:strs [roles]}]
              (some #(certified-roles (get % "role"))
                    roles)))

    (some? role)
    (filter #(select-role % role))

    ;; Begin Role-specific selectors: the following only work when
    ;; `role` is specified

    (some? legalAdherence)
    (filter #(= legalAdherence
                (get (select-role % role) "legal_adherence")))

    (some? loA)
    (filter #(= loA (get (select-role % role) "loa")))

    (some? compliancyVerified) ;; note spelling error in iSHARE spec
    (filter #((if compliancyVerified = not=) "yes"
              (get (select-role % role) "complaiancy_verified")))

    ;; End Role-specific selectors

    (some? companyEmail)
    (filter #(= companyEmail (get-in % ["additional_info" "company_email"])))

    (some? companyPhone)
    (filter #(= companyPhone (get-in % ["additional_info" "company_phone"])))


    (some? countriesOfOperation) ;; This is only a single country name!
    (filter (fn [{{:strs [countries_operation]} "additional_info"}]
              (some #(= countriesOfOperation %)
                    countries_operation)))

    (some? dataSpaceID)
    (filter (fn [{:strs [agreements]}]
              (some #(= dataSpaceID (get % "dataspace_id"))
                    agreements)))

    (some? dataSpaceTitle)
    (filter (fn [{:strs [agreements]}]
              (some #(= dataSpaceTitle (get % "dataspace_title"))
                    agreements)))

    (some? date_time)
    (filter (fn [{{:strs [start_date end_date]} "adherence"}]
              (when (and start_date end_date)
                (let [i (parse-time-stamp date_time)
                      s (parse-time-stamp start_date)
                      e (parse-time-stamp end_date)]
                  (and (or (.equals s i)
                           (.isBefore s i))
                       (or (.equals e i)
                           (.isAfter e i)))))))

    (some? eori)
    (filter (wildcard-pred "party_id" eori))

    (some? framework)
    (filter (fn [{:strs [agreements]}]
              (some #(= framework (get % "framework")) agreements)))

    (some? name)
    (filter (wildcard-pred "party_name" name))

    (some? publiclyPublishable)
    (filter #(= publiclyPublishable (get-in % ["additional_info" "publicly_publishable"])))

    (some? registarSatelliteID)
    (filter #(= registarSatelliteID (get % "registrar_id")))

    (some? sectorIndustry)
    (filter (fn [{{:strs [sector_industry]} "additional_info"}]
              (some #(= sectorIndustry %)
                    sector_industry)))

    (some? subjectName)
    (filter (fn [{:strs [certificates]}]
              (some #(= subjectName (get % "subject_name"))
                    certificates)))

    (some? tags)
    (filter #(has-tags? % tags))

    (some? webSiteUrl)
    (filter #(= webSiteUrl (get-in % ["additional_info" "website"])))

    :always
    (paginate (or page 1) "parties_info")))

(defn party
  [{:strs [parties] :as _source} party-id]
  (some #(when (= party-id (get % "party_id"))
           %)
        parties))
