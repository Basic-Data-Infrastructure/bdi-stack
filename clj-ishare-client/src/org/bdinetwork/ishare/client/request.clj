;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.request
  "Builder functions for iSHARE client requests.

  These functions take a request map and some number of arguments and
  return an updated request map, cumulating in a ring-style HTTP
  request map.

  See `org.bdinetwork.ishare.client` for a description of request keys
  specific to the client."
  (:require [org.bdinetwork.ishare.jwt :as jwt]))

(defn satellite-request
  "Build a request to access the iSHARE satellite.

  Takes a base `request` and uses `:ishare/satellite-base-url` and
  `:ishare/satellite-id` to set the `:ishare/base-url` and
  `:ishare/server-id`."
  [{:ishare/keys [satellite-base-url satellite-id] :as request}]
  {:pre [satellite-base-url satellite-id]}
  (assoc request
         :ishare/base-url    satellite-base-url
         :ishare/server-id   satellite-id))

(defn access-token-request
  "Build a request for an access token.

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
  "Build a `satellite-request` for parties info.

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
  "Build a `satellite-request` for party info for a single party.

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
  "Build a `satellite-request` for the trusted list of CAs.

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
  "Build a request for the capabilities of the server.

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
  "Build a delegation evidence request.

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
