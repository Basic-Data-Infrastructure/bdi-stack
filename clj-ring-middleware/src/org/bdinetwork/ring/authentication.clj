;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.authentication
  (:require [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as status]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.authentication.client-assertion :as client-assertion]
            [org.bdinetwork.ring.diagnostic-context :refer [with-context]]))

(defn wrap-client-assertion
  "Provide a `/connect/token` endpoint.

   It requires both `ring.middleware.json/wrap-json-response` and
  `ring.middleware.params/wrap-params` to function and expects an
  `association` on the request which implements
  `org.bdinetwork.association/Association`."
  [f opts]

  {:pre [(:access-token-ttl-seconds opts)]}
  (let [jti-cache-atom (client-assertion/mk-jti-cache-atom)]
    (fn client-assertion-wrapper
      [{:keys [path-info uri] :as request}]
      (if (= "/connect/token" (or path-info uri))
        (client-assertion/client-assertion-response request (assoc opts :jti-cache-atom jti-cache-atom))
        (f request)))))

(defn wrap-access-token
  "Middleware to set client-id from access-token.

  Fetches access token as bearer token from authorization header.
  Sets `:client-id` on request if a valid access token is passed. If
  no bearer token is passed, passes request as is.

  If access token is invalid, return \"401 Unauthorized\" response,
  configurable in `opts` as `invalid-token-response`."
  [f {:keys [invalid-token-response]
      :or   {invalid-token-response {:status status/unauthorized
                                     :body   "Invalid access token"}}
      :as   opts}]
  (fn access-token-wrapper [request]
    (if-let [access-token (access-token/get-bearer-token request)]
      ;; This (if-let [... (try ...)] ...) construct is messy.
      ;;
      ;; We want to capture exceptions thrown when parsing access
      ;; tokens but exceptions in (f request) should be left
      ;; alone.
      (if-let [client-id (try (access-token/access-token->client-id access-token opts)
                              (catch Exception e
                                (log/error e "Invalid access token")
                                nil))]
        (with-context [:client-id client-id]
          (f (assoc request :client-id client-id)))
        invalid-token-response)
      (f request))))

(defn wrap-authentication
  "Middleware to add authentication and a `/connect/token` endpoint.

  See also `wrap-access-token` and `wrap-client-assertion`."
  [f {:keys [private-key server-id] :as opts}]
  {:pre [private-key server-id]}
  (-> f
      (wrap-client-assertion opts)
      (wrap-access-token opts)))
