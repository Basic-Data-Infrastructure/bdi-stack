;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client
  (:require [babashka.http-client :as http]
            [babashka.http-client.interceptors :as interceptors]
            [babashka.json :as json]
            [buddy.core.keys :as keys]
            [clojure.string :as string]
            [org.bdinetwork.ishare.jwt :as jwt]
            [clojure.tools.logging.readable :as log]
            [clojure.core.memoize :as memoize])
  (:import (java.net URI)))

(defn private-key
  "Read private key from file."
  [key-file]
  (keys/private-key key-file))

;; From https://dev.ishareworks.org/reference/jwt.html#refjwt
;;
;;  "Signed JWTs MUST contain an array of the complete certificate
;;   chain that should be used for validating the JWT’s signature in
;;   the x5c header parameter up until an Issuing CA is listed from
;;   the iSHARE Trusted List."
;;
;; Does this mean we don't need to include the trusted CAs in the x5c
;; chain?

(defn x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))




;; named request functions

(defn satellite-request
  [{:ishare/keys [satellite-base-url satellite-id] :as request}]
  {:pre [satellite-base-url satellite-id]}
  (assoc request
         :ishare/base-url    satellite-base-url
         :ishare/server-id   satellite-id))

(defn access-token-request
  "Create a request for an access token.

  Takes a base `request` that should include client info. Builds a
  client assertion for `client-id` to authenticate to `server-id`.

  The request `:ishare/operation` is `:access-token`."
  ([{:ishare/keys [client-id base-url server-id] :as request} path]
   {:pre [client-id base-url server-id]}
   (assoc request
          :ishare/operation    :access-token
          :path                (or path "connect/token")
          :method              :post
          :as                  :json
          :ishare/bearer-token nil
          :form-params         {"client_id"             client-id
                                "grant_type"            "client_credentials"
                                "scope"                 "iSHARE" ;; TODO: allow restricting scope?
                                "client_assertion_type" "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                                "client_assertion"      (jwt/make-client-assertion request)}
          ;; NOTE: body includes expiry information, which we could use
          ;; for automatic caching -- check with iSHARE docs to see if
          ;; that's always available
          :ishare/lens          [:body "access_token"]))
  ([request]
   (access-token-request request nil)))

;; Parties is a satellite endpoint; response will be signed by the
;; satellite, and we cannot use `/parties` endpoint to validate the
;; signature of the `/parties` request.

(defn parties-request
  "Create a `satellite-request` for parties info.

  Takes a base `request` and passes `params` as query params."
  [request params]
  (-> request
      (satellite-request)
      (assoc :ishare/operation    :parties
             :method              :get
             :path                "parties"
             :as                  :json
             :query-params        params
             :ishare/unsign-token "parties_token"
             :ishare/lens         [:body "parties_token"])))

(defn party-request
  "Create a `satellite-request` for party info for a single party.

  Takes a base `request` and `party-id` of the party info to be
  returned."
  [request party-id]
  (-> request
      (satellite-request)
      (assoc :ishare/operation    :party
             :method              :get
             :path                (str "parties/" party-id)
             :as                  :json
             :ishare/unsign-token "party_token"
             :ishare/lens         [:body "party_token"])))

(defn trusted-list-request
  "Create a `satellite-request` for the trusted list of CAs.

  Takes a base `request`"
  [request]
  (-> request
      (satellite-request)
      (assoc :ishare/operation    :trusted-list
             :method              :get
             :path                "trusted_list"
             :as                  :json
             :ishare/unsign-token "trusted_list_token"
             :ishare/lens         [:body "trusted_list_token"])))

(defn capabilities-request
  "Create a request for the capabilities of the server.

  Takes a base `request`."
  [request]
  (assoc request
         :ishare/operation    :capabilities
         :method              :get
         :path                "capabilities"
         :as                  :json
         :ishare/unsign-token "capabilities_token"
         :ishare/lens         [:body "capabilities_token"]))

(defn delegation-evidence-request
  "Create a delegation evidence request.

  Sets `:ishare/policy-issuer` key from the given `delegation-mask`,
  for use by `ishare-issuer-ar-interceptor` -- which can locate the
  correct Authorization Register for that issuer and
  `:ishare/dataspace-id`."
  [request {{:keys [policyIssuer]} :delegationRequest :as delegation-mask}]
  {:pre [delegation-mask policyIssuer]}
  (assoc request
         :ishare/operation     :delegation-evidence
         :method               :post
         :path                 "delegation"
         :as                   :json
         :json-params          delegation-mask
         :ishare/policy-issuer policyIssuer
         :ishare/unsign-token  "delegation_token"
         :ishare/lens          [:body "delegation_token"]))

(declare exec)



;; We moved to named request functions since those are more
;; ergonomic. They are more discoverable, can take additional
;; arguments and can be indivually documented.
;;
;; The old ishare->http-request functions are now based on the new
;; functions, and moved here for backward compatibility.

(defmulti  ishare->http-request
  "This method will be removed in a future version of the library.

  Use named *-request functions instead.  See also `party-request`,
  `access-token-request`."
  :ishare/message-type)

(defmethod ishare->http-request nil
  ;; named request function called. leave the request alone
  [request]
  request)

(defmethod ishare->http-request :access-token
  [request]
  (access-token-request request (:path request)))

(defmethod ishare->http-request :parties
  [request]
  (parties-request request (:ishare/params request)))

(defmethod ishare->http-request :party
  [request]
  (party-request request (:ishare/party-id request)))

(defmethod ishare->http-request :trusted-list
  [request]
  (trusted-list-request request))

(defmethod ishare->http-request :capabilities
  [request]
  (capabilities-request request))

(defmethod ishare->http-request :delegation
  [{{{:keys [policyIssuer]} :delegationRequest :as delegation-mask} :ishare/params :as request}]
  (assert (= policyIssuer (:ishare/policy-issuer request)))
  (delegation-evidence-request request delegation-mask))



;; Interceptors

(def unsign-token-interceptor
  {:name     ::unsign-token
   :description
   "A request with `:ishare/unsign prop` will unsign the jwt under `prop` in response body"
   :response (fn unsign-token-response [response]
               (let [k (get-in response [:request :ishare/unsign-token])]
                 (if (and k (get-in response [:body k]))
                   (update-in response [:body k] jwt/unsign-token)
                   response)))})

(defn json-response?
  [response]
  (when-let [type (get-in response [:headers "content-type"])]
    (string/starts-with? type "application/json")))

(def json-interceptor
  {:name     ::json
   :description
   "A request with `:as :json` will automatically get the
   \"application/json\" accept header and the response is decoded as JSON.

    When :json-params is present in the request, an
    \"application/json\" content-type header is added and the
    json-params will be serialized as JSON and used as the request
    body."
   :request  (fn json-request [{:keys [as json-params] :as request}]
               (cond-> request
                 (= :json as)
                 (-> (assoc-in [:headers :accept] "application/json")
                     ;; Read body as :string
                     ;; Mark request as amenable to json decoding
                     (assoc :as :string ::json true))

                 (contains? request :json-params) ;; use contains? to support `:json-params nil`
                 (-> (assoc-in [:headers "content-type"] "application/json")
                     (assoc :body (json/write-str json-params)))))
   :response (fn json-response [response]
               (if (and (get-in response [:request ::json])
                        (json-response? response))
                 (update response :body #(json/read-str % {:key-fn identity}))
                 response))})

(def bearer-token-interceptor
  {:name     ::bearer-token
   :description
   "A request with a non-nil `:ishare/bearer-token` will get an Authorization
   header for the bearer token added."
   :request  (fn bearer-token-request [{:ishare/keys [bearer-token] :as request}]
               (if bearer-token
                 (assoc-in request [:headers "Authorization"] (str "Bearer " bearer-token))
                 request))})

(def fetch-bearer-token-interceptor
  {:name    ::fetch-bearer-token
   :doc     "When request has no :ishare/bearer-token, fetch it from the endpoint.
When bearer token is not needed, provide a `nil` token"
   :request (fn fetch-bearer-token-request [request]
              (if (contains? request :ishare/bearer-token)
                request
                (let [response (-> request
                                   (select-keys  [:ishare/x5c
                                                  :ishare/private-key
                                                  :ishare/client-id
                                                  :ishare/server-id
                                                  :ishare/base-url
                                                  :ishare/satellite-id
                                                  :ishare/satellite-base-url
                                                  :ishare/fetch-party-info-fn])
                                   (access-token-request)
                                   exec)
                      token    (:ishare/result response)]
                  (when-not token
                    ;; FEEDBACK: bij invalid client op /token komt 202 status terug?
                    (throw (ex-info "Error fetching access token" {:response response})))
                  (assoc request :ishare/bearer-token token))))})

(def lens-interceptor
  {:name     ::lens
   :description
   "If request contains :ishare/lens path, put the object at path in
   reponse, under :ishare/result"
   :response (fn lens-response [response]
               (if-let [path (get-in response [:request :ishare/lens])]
                 (assoc response :ishare/result (get-in response path))
                 response))})

(def ^:dynamic log-interceptor-atom nil)

(def log-interceptor
  {:name     ::log
   :response (fn log-response [r]
               (when log-interceptor-atom
                 (swap! log-interceptor-atom conj r))
               r)})

(def logging-interceptor
  {:name     ::logging
   :response (fn logging-response [{:keys [request] :as response}]
               (log/debug {:request  (select-keys request [:method :uri])
                           :response (select-keys response [:status])})
               response)})


(defn redact-path
  [r p]
  (if (get-in r p)
    (assoc-in r p "REDACTED")
    r))

(defn redact-body
  "Remove sensitive params from request body (for logging)"
  [body]
  (if (string? body)
    (string/replace body #"(client_assertion=)[^&]+" "$1REDACTED")
    body))

(defn redact-request
  [request]
  (-> request
      (redact-path [:ishare/private-key])
      (redact-path [:ishare/x5c])
      (dissoc :interceptors)
      (redact-path [:form-params "client_assertion"])
      (update :headers redact-path ["authorization"])
      (update :body redact-body)
      (dissoc :client)))

(def unexceptional-statuses
  #{200 201 202 203 204 205 206 207 300 301 302 303 304 307})

(def throw-on-exceptional-status-code
  "Response: throw on exceptional status codes. Strips out client info and private information"
  {:name ::throw-on-exceptional-status-code
   :response (fn throw-on-exceptional-status-code-response [resp]
               (if-let [status (:status resp)]
                 (if (or (false? (some-> resp :request :throw))
                         (contains? unexceptional-statuses status))
                   resp
                   (throw (ex-info (str "Exceptional status code: " status) (update resp :request redact-request))))
                 resp))})

(defn resolve-uri [base-url path]
  (let [base-url (if  (string/ends-with? base-url "/")
                   base-url
                   (str base-url "/"))]
    (-> base-url
        (URI.)
        (.resolve (URI. path))
        (.normalize)
        (str))))

(def build-uri-interceptor
  {:name ::build-uri
   :request (fn build-uri-request [{:keys [path ishare/base-url] :as request}]
              (if (and path base-url)
                (assoc request :uri (resolve-uri base-url path))
                request))})



;; This is a workaround
;;
;; The current (as of 2024-10-02) iSHARE satellite implementations
;; return out-of-spec information about a party's authorization
;; registries.
;;
;; According to the v2.0 specification, this information should be
;; provided as a collection under the `auth_registries` key -- see the
;; #/components/schemas/Party entry in iSHARE scheme 2.0 --
;; https://app.swaggerhub.com/apis/iSHARE/iSHARE_Scheme_Specification/2.0#/Party
;; but the satellite actually returns this information in a different
;; form under the `authregistery` key, which is undocumented.

(defn- party-info->auth-registry
  "Workaround `:auth_registries` data is provided as `:authregistery` in current ishare satellite."
  [{:keys [auth_registries authregistery] :as _party_info}]
  (or auth_registries
      (map (fn upgrade-authregistery
             [{:keys [dataspaceID authorizationRegistryName
                      authorizationRegistryID authorizationRegistryUrl]}]
             {:dataspace_id dataspaceID
              :id           authorizationRegistryID
              :name         authorizationRegistryName
              :url          authorizationRegistryUrl})
           authregistery)))

(def instant-formatter
  java.time.format.DateTimeFormatter/ISO_ZONED_DATE_TIME)

(defn parse-instant
  [s]
  {:pre [s]}
  (-> (java.time.ZonedDateTime/parse s instant-formatter)
      (.toInstant)))

(defn party-adherence-issue
  "If party info is not currently adherent, returns an issue map
  of :issue message and :info data."
  [{{:keys [status start_date end_date]} :adherence
    :keys                                [party_id] :as party-info}]
  {:pre [party-info]}
  (let [now (java.time.Instant/now)]
    (cond
      (not= "Active" status)
      {:issue "Server party not active"
       :info  {:status     status
               :party_id   party_id
               :start_date start_date
               :end_date   end_date}}

      (not start_date)
      {:issue "No start_date"
       :info  {:status     status
               :party_id   party_id
               :start_date start_date
               :end_date   end_date}}

      (not end_date)
      {:issue "No end_date"
       :info  {:status     status
               :party_id   party_id
               :start_date start_date
               :end_date   end_date}}

      (not (.isBefore (parse-instant start_date) now))
      {:issue "Server party not yet active"
       :info  {:status     status
               :party_id   party_id
               :now        now
               :start_date start_date
               :end_date   end_date}}

      (not (.isBefore  now (parse-instant end_date)))
      {:issue "Server party not active anymore"
       :info  {:status     status
               :party_id   party_id
               :now        now
               :start_date start_date
               :end_date   end_date}})))

(defn fetch-party-info*
  "Fetch party info from satellite.

  Usually you'll want to use `fetch-party-info` instead."
  [client-info party-id]
  (-> (party-request client-info party-id)
      exec
      :ishare/result
      :party_info))

(defn mk-cached-fetch-party-info
  "Create a cached version of `fetch-party-info*`"
  [ttl-ms]
  (memoize/ttl fetch-party-info* {} :ttl/threshold ttl-ms))

(def ^{:doc "Cached version of `fetch-party-info*` with a one hour cache of results."
       :arglists '([request party-id])}
  fetch-party-info-default
  (mk-cached-fetch-party-info 3600000))

(defn fetch-party-info
  "Request party info from satellite with optional cache.

  Takes `:ishare/fetch-party-info-fn` from `request` to execute request. The
  default value for this function is `fetch-party-info-default`, which
  caches its result for one hour."
  [{:ishare/keys [fetch-party-info-fn] :as request :or {fetch-party-info-fn fetch-party-info-default}}
   party-id]
  (fetch-party-info-fn (select-keys request [:ishare/x5c
                                             :ishare/private-key
                                             :ishare/client-id
                                             :ishare/satellite-id
                                             :ishare/satellite-base-url])
                       party-id))

(defn- check-server-adherence
  "Fetch party for `:ishare/server-id` and raise an exception if the
  party is not adherent.

  Will always assume the server with `server-id` equal to
  `satellite-id` is adherent.

  Returns `request` with `:ishare/server-name` set from party info it
  isn't already present."
  [{:ishare/keys [server-id satellite-id]
    :as          request}]
  {:pre [satellite-id server-id]}
  (if (= satellite-id server-id)
    (assoc request :ishare/server-name "Association Register")
    (if-let [{:keys [party_name] :as party-info} (fetch-party-info request server-id)]
      (if-let [{:keys [issue info]} (party-adherence-issue party-info)]
        (throw (ex-info issue info))
        (assoc request :ishare/server-name party_name))
      (throw (ex-info "Party was not found" {:server-id    server-id
                                                 :satellite-id satellite-id})))))

(def ishare-server-adherence-interceptor
  {:name ::ishare-server-status-interceptor
   :doc "Before accessing a server, check that it is still adherent.

   If request has the `false` value for
   `:ishare/check-server-adherence?`, this check will be skipped."
   :request (fn [{:ishare/keys [check-server-adherence?] :as request}]
              (if (false? check-server-adherence?)
                request
                (-> request
                    (check-server-adherence)
                    (assoc :ishare/check-server-adherence? false))))})

(defn- fetch-issuer-ar
  "If request contains `policy-issuer` and no `server-id` + `base-url`,
  set `server-id` and `base-url` to issuer's authorization registry
  for dataspace."
  [{:ishare/keys [policy-issuer dataspace-id server-id base-url]
    :as          request}]
  (if (or (not (and policy-issuer dataspace-id))
          (and server-id base-url))
    request
    (if-let [{:keys [name id url]}
             (->> (fetch-party-info request policy-issuer)
                  (party-info->auth-registry)
                  (filter #(= dataspace-id (:dataspace_id %)))
                  first)]
      (assoc request
             :ishare/server-id id
             :ishare/server-name name
             :ishare/base-url url)
      (throw (ex-info (str "Can't find authorization register for " policy-issuer)
                      {:dataspace-id dataspace-id
                       :policy-issuer policy-issuer})))))

(def fetch-issuer-ar-interceptor
  {:name    ::fetch-issuer-ar
   :request fetch-issuer-ar})

(def ishare-interpreter-interceptor
  {:name ::ishare-interpretor-interceptor
   :doc  "This is a deprecated interceptor. Use named request functions instead."
   :request ishare->http-request})

(def interceptors
  [ishare-interpreter-interceptor
   fetch-issuer-ar-interceptor
   ishare-server-adherence-interceptor
   throw-on-exceptional-status-code
   log-interceptor
   logging-interceptor
   lens-interceptor
   unsign-token-interceptor
   build-uri-interceptor
   fetch-bearer-token-interceptor
   bearer-token-interceptor
   interceptors/construct-uri
   interceptors/accept-header
   interceptors/query-params
   interceptors/form-params
   json-interceptor ;; should be between decode-body and
   ;; throw-on-exceptional-status-code, so that JSON
   ;; error messages are decoded
   interceptors/decode-body
   interceptors/decompress-body])



;; Client / exec functions

(def ^:dynamic http-client nil)

(def timeout-ms 10000)

(def http-client-opts
  {:follow-redirects :normal
   :connect-timeout  timeout-ms
   :request          {:headers {:accept          "*/*"
                                :accept-encoding ["gzip" "deflate"]
                                :user-agent      "clj-ishare-client"}}})

(def default-http-client
  (delay (http/client http-client-opts)))

(defn exec
  [request]
  (http/request (assoc request
                       :client (or http-client @default-http-client)
                       :interceptors interceptors
                       :timeout timeout-ms)))



(comment
  (def client-data
    {:ishare/client-id   "EU.EORI.NLSMARTPHON"
     :ishare/x5c         (x5c "test/pem/client.cert.pem")
     :ishare/private-key (private-key "test/pem/client.key.pem")})

  (def client-data
    {:ishare/client-id   "EU.EORI.NLFLEXTRANS"
     :ishare/x5c         (x5c "credentials/EU.EORI.NLFLEXTRANS.crt")
     :ishare/private-key (private-key "credentials/EU.EORI.NLFLEXTRANS.pem")})

  (def ishare-ar-request
    {:ishare/base-url    "https://ar.isharetest.net/"
     :ishare/server-id   "EU.EORI.NL000000004"
     :ishare/client-id   "EU.EORI.NLSMARTPHON"
     :ishare/x5c         (x5c "credentials/EU.EORI.NLSMARTPHON.crt")
     :ishare/private-key (private-key "credentials/EU.EORI.NLSMARTPHON.pem")})

  (def poort8-ar-request
    {:ishare/base-url    "https://tsl-ishare-dataspace-coremanager-preview.azurewebsites.net/api/ishare/"
     :ishare/server-id   "EU.EORI.NLP8TSLAR1"
     :ishare/client-id   "EU.EORI.NLPRECIOUSG"
     :ishare/x5c         (x5c "credentials/EU.EORI.NLPRECIOUSG.crt")
     :ishare/private-key (private-key "credentials/EU.EORI.NLPRECIOUSG.pem")})

  (def delegation-evidence
    {"delegationEvidence"
     {"notBefore"    0
      "notOnOrAfter" 0
      "policyIssuer" "EU.EORI.NLSMARTPHON"
      "target"       {"accessSubject" "EU.EORI.NLPRECIOUSG"}
      "policySets"   [{"policies" [{"rules"  [{"effect" "Permit"}]
                                    "target" {"resource" {"type"        "klantordernummer"
                                                          "identifiers" ["112233"]
                                                          "attributes"  ["*"]}
                                              "actions"  ["BDI.PICKUP"]}}]
                       "target"   {"resource" {"type"        "klantordernummer"
                                               "identifiers" ["112233"]
                                               "attributes"  ["*"]}
                                   "actions"  ["BDI.PICKUP"]
                                   ;; `licenses` is required, but seems
                                   ;; to fit pretty badly, let's go
                                   ;; for "0001" -- "Re-sharing with
                                   ;; Adhering Parties only"
                                   "environment" {"licenses" ["0001"]}}}]}})

  (def delegation-mask
    {:delegationRequest
     {:policyIssuer "EU.EORI.NLSMARTPHON"
      :target       {:accessSubject "EU.EORI.NLPRECIOUSG"}
      :policySets   [{:policies [{:rules  [{:effect "Permit"}]
                                  :target {:resource    {:type        "klantordernummer"
                                                         :identifiers ["112233"]
                                                         :attributes  ["*"]}
                                           :actions     ["BDI.PICKUP"]
                                           :environment {:licenses ["0001"]
                                                         :serviceProviders []}}}]}]}})

  (-> client-data
      (assoc :ishare/satellite-id (System/getenv "SATELLITE_ID"))
      (assoc :ishare/satellite-base-url (System/getenv "SATELLITE_ENDPOINT"))
      (satellite-request)
      (assoc :ishare/message-type :access-token)
      exec
      :ishare/result)

  (-> ishare-ar-request
      (assoc :ishare/message-type :ishare/policy ;; ishare-ar specific call
             :ishare/params delegation-evidence)
      exec
      :ishare/result)

  (-> ishare-ar-request
      (assoc :ishare/message-type :delegation ;; standardized call
             :ishare/params delegation-mask)
      exec
      :ishare/result))
