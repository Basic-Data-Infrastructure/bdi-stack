(ns org.bdinetwork.assocation-register.data-source
  (:require [clojure.spec.alpha :as s]
            [org.bdinetwork.assocation-register.data-source.party :as-alias party]
            [org.bdinetwork.assocation-register.data-source.agreement :as-alias agreement]
            [org.bdinetwork.assocation-register.data-source.adherance :as-alias adherance]
            [org.bdinetwork.assocation-register.data-source.additional-info :as-alias additional-info]
            [org.bdinetwork.assocation-register.data-source.certificate :as-alias certificate]
            [org.bdinetwork.assocation-register.data-source.spor :as-alias spor]
            [org.bdinetwork.assocation-register.data-source.role :as-alias role]
            [org.bdinetwork.assocation-register.data-source.auth-registry :as-alias auth-registry]))

(s/def ::parties-params
  (s/and (s/keys :opt-un [::active_only
                          ::name
                          ::eori
                          ::certified_only
                          ::date_time
                          ::adheranceStatus
                          ::adheranceStartdate
                          ::adheranceEnddate
                          ::registerSatelliteID
                          ::webSiteUrl
                          ::companyEmail
                          ::companyPhone
                          ::publiclyPublishable
                          ::tags
                          ::framework
                          ::subjectName
                          ::role
                          ::loA
                          ::compliancyVerified
                          ::legalAdherence
                          ::authorizationRegistryID
                          ::authorizationRegistryName
                          ::dataSpaceID
                          ::dataSpaceTitle
                          ::countriesOfOperation
                          ::sectorIndustry
                          ::page
                          ::certificate_subject_name])
         #(seq %)))

(s/def ::active_only boolean?) ;; HOW? these are strings
(s/def ::name )

(s/def ::party
  (s/keys :req-un [::party/party_id
                   ::party/party_name
                   ::party/capability_url
                   ::party/registrar_id
                   ::party/adherance
                   ::party/additional_info
                   ::party/agreements
                   ::party/certificates
                   ::party/spor
                   ::party/roles
                   ::party/auth_registries]))

(s/def ::party/party_id string?)
(s/def ::party/party_name string?)
(s/def ::party/capability_url string?)
(s/def ::party/registrar_id string?)

(s/def ::party/adherance
  (s/keys :opt-un [::adherance/status
                   ::adherance/start_date
                   ::adherance/end_date]))

(s/def ::adherance/status #{"Active" "Pending" "NotActive" "Revoked"})
(s/def ::iso-date
  (s/and string?
         #(re-matches #"\d{4}-\d{2}-\d{2}T\d\d:\d\d:\d\d(\.0+)?Z")))

(s/def ::adherance/start_date ::iso-date)
(s/def ::adherance/end_date ::iso-date)

(s/def ::party/additional_info
  (s/keys :opt-un [::additional-info/description
                   ::additional-info/logo
                   ::additional-info/website
                   ::additional-info/company_phone
                   ::additional-info/company_email
                   ::additional-info/publically_publishable
                   ::additional-info/countries_operation
                   ::additional-info/sector_industry
                   ::additional-info/tags]))

(s/def ::additional-info/description string?)
(s/def ::additional-info/logo string?)
(s/def ::additional-info/website string?)
(s/def ::additional-info/company_phone string?)
(s/def ::additional-info/company_email string?)
(s/def ::additional-info/publically_publishable boolean?)

;; https://dev.ishare.eu/satellite/parties.html
;;    Array of Objects. Contained in additional_info.
;;    An array of ISO names of the countries where the party operates.
;;
;; Presumably these are Strings, not Objects

(s/def ::additional-info/countries_operation (s/coll-of string?))

;; https://dev.ishare.eu/satellite/parties.html
;;    Array of Objects. Contained in additional_info.
;;    An array of GICS based sectors/industry that party serves in.
;;
;; Presumably these are Strings, not Objects

(s/def ::additional-info/sector_industry (s/coll-of string?))
(s/def ::additional-info/tags string?) ;; NOT a collection

(s/def ::party/agreement
  (s/keys :opt-un [::agreement/type
                   ::agreement/title
                   ::agreement/status
                   ::agreement/sign_date
                   ::agreement/expiry_date
                   ::agreement/hash_file
                   ::agreement/framework
                   ::agreement/dataspace_id
                   ::agreement/dataspace_title
                   ::agreement/complaiancy_verified ; typo in specification
                   ]))

(s/def ::agreement/type string?)
(s/def ::agreement/title string?)
(s/def ::agreement/status #{"Draft" "Signed" "Accepted" "Obsolete"})
(s/def ::agreement/sign_date ::iso-date)
(s/def ::agreement/expiry_date ::iso-date)
(s/def ::agreement/hash_file string?) ;; no info on how to calculate the hash
(s/def ::agreement/framework #{"iSHARE"})
(s/def ::agreement/dataspace_id string?)
(s/def ::agreement/dataspace_title string?)
(s/def ::agreement/complaiancy_verified #{"Yes" "No" "Not Applicable"})

(s/def ::party/agreements
  (s/coll-of ::party/agreement))

(s/def ::party/certificates
  (s/coll-of ::party/certificate))

(s/def ::party/certificate
  (s/keys :opt-un [::certificate/subject_name
                   ::certificate/certificate_type
                   ::certificate/enabled_from
                   ::certificate/x5c
                   ::certificate/x5t#s256]))

(s/def ::certificate/subject_name string?)
(s/def ::certificate/certificate_type string?)
(s/def ::certificate/enabled_from ::iso-date)
(s/def ::certificate/x5c string?)
(s/def ::certificate/x5t#s256 string?)

(s/def ::party/spor
  (s/keys :opt-un [::spor/signed_request]))

(s/def ::spor/signed_request string?)

(s/def ::party/roles
  (s/coll-of ::party/role))

(s/def ::party/role
  (s/keys :opt-un [::role/role
                   ::role/start_date
                   ::role/end_date
                   ::role/loa
                   ::role/compliancy_verified
                   ::role/legal_adherence]))

(s/def ::role/role
  #{"ServiceConsumer" "ServiceProvider" "EntitledParty"
    "AuthorisationRegistry" "IdentityProvider" "IdentityBroker"
    "iShareSatellite"})

(s/def ::role/start_date ::iso-date)
(s/def ::role/end_date ::iso-date)
(s/def ::role/loa integer?)
(s/def ::role/compliancy_verified boolean?)
(s/def ::role/legal_adherence boolean?)

(s/def ::party/auth_registries
  (s/coll-of ::party/auth_registry))

(s/def ::party/auth_registry
  (s/keys :opt-un [::auth-registry/name
                   ::auth-registry/id
                   ::auth-registry/url
                   ::auth-registry/dataspace_id
                   ::auth-registry/dataspace_name]))

(s/def ::auth-registry/name string?)
(s/def ::auth-registry/id string?)
(s/def ::auth-registry/url string?)
(s/def ::auth-registry/dataspace_id string?)
(s/def ::auth-registry/dataspace_name string?)

(defprotocol DataSource
  :extend-via-metadata true
  (parties [this parties-params]
    "Returns parties data")
  )
