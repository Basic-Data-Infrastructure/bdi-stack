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

(defn not-implemented
  [param & _]
  (throw (ex-info "Parameter not implemented" {:param param})))

(def certified-roles
  ;; According to the iShare specs, iShareSatellite is NOT a certified
  ;; role.
  ;;
  ;; https://framework.ishare.eu/main-aspects-of-the-ishare-trust-framework/framework-and-roles
  #{"IdentityProvider" "IdentityBroker" "AuthorisationRegistry"})

(defn parties
  [{:keys [parties] :as _source} {:keys [active_only
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
    (filter #(= "Active" (get-in % ["adherance" "status"])))

    (some? adherenceEnddate)
    (filter #(= adherenceEnddate (get-in % ["adherence" "end_date"])))

    (some? adherenceStartdate)
    (filter #(= adherenceEnddate (get-in % ["adherence" "start_date"])))

    ;; FIXME: take start and end date into account
    (some? adherenceStatus)
    (filter #(= adherenceStatus (get-in % ["adherence" "status"])))

    (some? authorizationRegistryID)
    (filter (fn [{:strs [auth_registries]}]
              (some #(= authorizationRegistryID (get % "id")))))

    (some? authorizationRegistryName)
    (filter (fn [{:strs [auth_registries]}]
              (some #(= authorizationRegistryName (get % "name")))))

    (some? certificate_subject_name)
    (not-implemented "certificate_subject_name")

    (some? certified_only)
    (filter (fn [{:strs [roles]}]
              (some certified-roles roles)))

    (some? companyEmail)
    (filter #(= companyEmail (get-in % ["additional_info" "company_email"])))

    (some? companyPhone)
    (filter #(= companyPhone (get-in % ["additional_info" "company_phone"])))

    (some? compliancyVerified)
    (filter (fn [{:strs [roles]}]
              (some #(= compliancyVerified (get % "compliancy_verified"))
                    roles)))

    (some? countriesOfOperation) ;; This is only a single country name!
    (filter (fn [{{:strs [countries_operation]} "additional_info"}]
              (some #(= countriesOfOperation %) countries_operation)))

    (some? dataSpaceID)
    (filter (fn [{:strs [auth_registries]}]
              (some #(= dataSpaceID (get % "dataspace_id")))))

    (some? dataSpaceTitle)
    (filter (fn [{:strs [auth_registries]}]
              (some #(= dataSpaceID (get % "dataspace_name")))))

    (some? date_time)
    (not-implemented "date_time")

    (some? eori)
    (filter (wildcard-pred "eori" eori))

    (some? framework)
    (filter (fn [{:strs [agreements]}]
              (some #(= framework (get % "framework")) agreements)))

    (some? legalAdherence)
    (not-implemented "legalAdherence")

    (some? loA)
    (filter (fn [{:strs [roles]}]
              (some #(= loA (get % "loa"))
                    roles)))

    (some? name)
    (filter (wildcard-pred "name" name))

    ;; TODO: page here?

    (some? publiclyPublishable)
    (filter #(= publiclyPublishable (get-in % ["additional_info" "publicly_publishable"])))

    (some? registarSatelliteID)
    (not-implemented "registrarSatelliteID")

    (some? role)
    (filter (fn [{:strs [roles]}]
              (some #(= role (get % "role"))
                    roles)))

    (some? sectorIndustry)
    (filter (fn [{{:strs [sector_industry]} "additional_info"}]
              (some #(= sectorIndustry %) sector_industry)))

    (some? subjectName)
    (not-implemented "subjectName")

    (some? tags)
    ;; TODO: apparently, tags is a whitespace separated string
    (filter #(string/includes? (get-in % ["additional_info" "tags"]) tags))

    (some? webSiteUrl)
    (filter #(= webSiteUrl (get-in % ["additional_info" "website"])))))
