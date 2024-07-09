(ns org.bdinetwork.assocation-register.data-source
  (:require [org.bdinetwork.assocation-register.ishare-validator :refer [parse-yaml validate]]
            [clojure.string :as string]))

(defn read-data-source
  [in]
  (let [source (parse-yaml in)]
    (when-let [issues (validate (get source "parties")
                                ["components" "schemas" "PartiesInfo" "properties" "data"])]
      (throw (ex-info "Invalid party in data source" {:issues issues})))
    source))

(defn wildcard-pred
  [prop val]
  (if-let [[_ start end] (re-find #"(.*)\*(.*)" val)]
    (fn [party]
      (let [v (get party prop)]
        (and (string/starts-with? v start)
             (string/ends-with? v end))))
    #(= val (get % prop))))

;; If we decide to indefinitely not implement particular params
;; we should return a particular error status in the HTTP API
;; (maybe 501 Not Implemented)
(defn not-implemented
  [param & _]
  (throw (ex-info "Parameter not implemented" {:param param})))

(def certified-roles
  ;; According to the iShare specs, iShareSatellite is NOT a certified
  ;; role.
  ;;
  ;; https://framework.ishare.eu/main-aspects-of-the-ishare-trust-framework/framework-and-roles
  #{"IdentityProvider" "IdentityBroker" "AuthorisationRegistry"})

(defn select-role
  [{:strs [roles] :as _party} role]
  (first (filter #(= role (get % "role")) roles)))

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
    (not-implemented "certificate_subject_name")

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
    (not-implemented "date_time")

    (some? eori)
    (filter (wildcard-pred "party_id" eori))

    (some? framework)
    (filter (fn [{:strs [agreements]}]
              (some #(= framework (get % "framework")) agreements)))

    (some? name)
    (filter (wildcard-pred "party_name" name))

    ;; TODO: page here?

    (some? publiclyPublishable)
    (filter #(= publiclyPublishable (get-in % ["additional_info" "publicly_publishable"])))

    (some? registarSatelliteID)
    (filter #(= registarSatelliteID (get % "registrar_id")))

    (some? sectorIndustry)
    (filter (fn [{{:strs [sector_industry]} "additional_info"}]
              (some #(= sectorIndustry %)
                    sector_industry)))

    (some? subjectName)
    (not-implemented "subjectName")

    (some? tags)
    ;; TODO: apparently, tags is a whitespace separated string
    (filter #(string/includes? (get-in % ["additional_info" "tags"]) tags))

    (some? webSiteUrl)
    (filter #(= webSiteUrl (get-in % ["additional_info" "website"])))))
