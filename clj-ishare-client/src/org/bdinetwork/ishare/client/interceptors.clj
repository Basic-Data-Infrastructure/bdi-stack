;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.interceptors
  "Provides iSHARE client interceptors for processing request and responses.

  This namespace does not provide not a stable API. Incompatible
  changes to interceptors (including removal and renaming) should be
  expected."
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [clojure.core.memoize :as memoize]
            [clojure.string :as string]
            [clojure.tools.logging.readable :as log]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.ishare.jwt :as jwt])
  (:import (java.net URI)))

;; TODO: Token cache
;; TODO: party cache configuration via data instead of function

;; Interceptors

(def unsign-token-interceptor
  {:name     ::unsign-token
   :description
   "A request with `:ishare/unsign-token prop` will unsign the jwt
   under `prop` in response body"
   :response (fn unsign-token-response [response]
               (let [k (get-in response [:request :ishare/unsign-token])]
                 (if (and k (get-in response [:body k]))
                   (update-in response [:body k] jwt/unsign-token)
                   response)))})

(defn- json-response?
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

(defn- exec-in-interceptor
  "Exectute request using the :client present.

  Assumes :client and :interceptors are provided."
  [request]
  {:pre [(:interceptors request)
         (:client request)]}
  (http/request request))

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
                                                  :ishare/fetch-party-info-fn
                                                  :client
                                                  :interceptors
                                                  :timeout])
                                   (request/access-token-request)
                                   exec-in-interceptor)
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
               (log/debug {:request  (select-keys request [:method :uri :ishare/client-id])
                           :response (select-keys response [:status :ishare/result])})
               response)})


(defn redact-path
  [r p]
  (if (get-in r p)
    (assoc-in r p "REDACTED")
    r))

(defn redact-body
  "Remove sensitive params from request body (for logging)."
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
  "Throw on exceptional status codes.

  Throws with response data. Strips client info and private information from the thrown response."
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

(def ^:private instant-formatter
  java.time.format.DateTimeFormatter/ISO_ZONED_DATE_TIME)

(defn- parse-instant
  [s]
  {:pre [s]}
  (-> (java.time.ZonedDateTime/parse s instant-formatter)
      (.toInstant)))

(defn- party-adherence-issue
  "Returns adherence issue with party info.

  If party info is not currently adherent, returns an issue map
  of :issue message and :info data.

  If party is adherent, returns `nil`."
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
  (-> (request/party-request client-info party-id)
      exec-in-interceptor
      :ishare/result
      :party_info))

(defn mk-cached-fetch-party-info
  "Create a cached version of `fetch-party-info*`."
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
                                             :ishare/satellite-base-url
                                             :client
                                             :interceptors
                                             :timeout])
                       party-id))

(defn- check-server-adherence
  "Check `:ishare/server-id` for adherence.

  Fetches party for `:ishare/server-id` from satellite and raise an
  exception if the party is not adherent.

  Will always assume the server with `server-id` equal to
  `satellite-id` is adherent.

  Returns an updated `request` if the server is adherent:
  - adds `:ishare/server-name` from party info if not already present
  - adds `:ishare/server-adherent?` `true`"
  [{:ishare/keys [server-id satellite-id]
    :as          request}]
  {:pre [satellite-id server-id]}
  (if (= satellite-id server-id)
    (assoc request
           :ishare/server-name "Association Register"
           :ishare/server-adherent? true)
    (if-let [{:keys [party_name] :as party-info} (fetch-party-info request server-id)]
      (if-let [{:keys [issue info]} (party-adherence-issue party-info)]
        (throw (ex-info issue info))
        (assoc request
               :ishare/server-name party_name
               :ishare/server-adherent? true))
      (throw (ex-info "Party was not found" {:server-id    server-id
                                             :satellite-id satellite-id})))))

(def ishare-server-adherence-interceptor
  {:name ::ishare-server-status-interceptor
   :doc "Before accessing a server, check that it is still adherent.

   If request has the `true` value for `:ishare/server-adherent?` the
   sever is assumed to be adherent and the check is skipped.

   If request has the `false` value for
   `:ishare/check-server-adherence?`, the check is skipped."
   :request (fn [{:ishare/keys [server-adherent? check-server-adherence?] :as request}]
              (if (or server-adherent? (false? check-server-adherence?))
                request
                (check-server-adherence request)))})

(defn- fetch-issuer-ar
  "Set server info to authorization register of `:ishare/policy-issuer`.

  If request contains `policy-issuer` and no `server-id` + `base-url`,
  set `server-id` and `base-url` to issuer's authorization registry
  for dataspace."
  [{:ishare/keys [policy-issuer dataspace-id server-id base-url]
    :as          request}]

  (when (and policy-issuer
             (not server-id))
    (assert dataspace-id))
  
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
   :doc     "If request contains `policy-issuer` and no `server-id` + `base-url`,
  set `server-id` and `base-url` to issuer's authorization registry
  for dataspace."
   :request fetch-issuer-ar})
