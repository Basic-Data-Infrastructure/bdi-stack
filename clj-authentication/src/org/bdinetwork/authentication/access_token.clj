;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication.access-token
  (:require [buddy.sign.jwt :as jwt])
  (:import java.time.Instant
           java.util.UUID))

(def default-access-token-ttl-seconds 600)

(defn- seconds-since-unix-epoch
  "Current number of seconds since the UNIX epoch."
  []
  (.getEpochSecond (Instant/now)))

(defn mk-access-token
  "Create a signed access token.

  The token is signed with `private-key` and will expire in
  `ttl-seconds`.

  Warning: the access token is not encrypted; all data in `claims` is
  directly readable from the access token. Do not store private data
  in `claims`."
  [{:keys [client-id server-id private-key access-token-ttl-seconds]
    :or {access-token-ttl-seconds default-access-token-ttl-seconds}}]
  {:pre [client-id server-id private-key access-token-ttl-seconds]}
  (let [now (seconds-since-unix-epoch)
        exp (+ access-token-ttl-seconds now)]
    (jwt/sign {:iss server-id
               :aud server-id ;; TODO: allow for more restricted audience?
               :sub client-id
               :iat now
               :nbf now
               :exp exp
               :jti (str (UUID/randomUUID))}
              private-key
              {:header {:typ "JWT"}
               :alg    :rs256})))

(defn access-token->client-id
  "Validate `access-token` and return client from claims.

  Throws exception if access token is invalid."
  [access-token
   {:keys [server-id public-key access-token-ttl-seconds]
    :or {access-token-ttl-seconds default-access-token-ttl-seconds}}]
  {:pre [server-id public-key access-token-ttl-seconds]}
  (let [decoded (jwt/decode-header access-token)]
    (when (not= {:alg :rs256 :typ "JWT"} decoded)
      (throw (ex-info "Invalid JWT header" {:header decoded}))))
  (let [{:keys [iss sub aud iat nbf exp jti]} (jwt/unsign access-token public-key {:alg :rs256 :leeway 5})]
    (cond (not= iss server-id)
          (throw (ex-info "Claim iss is not server-id" {:iss iss :server-id server-id}))

          (not= aud server-id)
          (throw (ex-info "Claim aud is not server-id" {:aud aud :server-id server-id}))

          (not (some? sub))
          (throw (ex-info "Claim sub missing" {:sub sub}))

          (not (int? iat))
          (throw (ex-info "Claim iat is not an integer" {:iat iat}))

          (not (int? exp))
          (throw (ex-info "Claim exp is not an integer" {:exp exp}))

          (not (int? nbf))
          (throw (ex-info "Claim nbf is not an integer" {:nbf nbf}))

          (not= iat nbf)
          (throw (ex-info "Claim nbf is not iat" {:nbf nbf :iat iat}))

          (not= access-token-ttl-seconds (- exp iat))
          (throw (ex-info "Expiry is incorrect" {:exp exp :iat iat :access-token-ttl-seconds access-token-ttl-seconds}))

          (not (string? jti))
          (throw (ex-info "Claim jti is not a string" {:jti jti}))

          :else
          sub)))

(defn get-bearer-token
  "Extract bearer token from request."
  [request]
  (let [auth-header (get-in request [:headers "authorization"])
        [_ token]   (and auth-header (re-matches #"Bearer (\S+)" auth-header))]
    token))
