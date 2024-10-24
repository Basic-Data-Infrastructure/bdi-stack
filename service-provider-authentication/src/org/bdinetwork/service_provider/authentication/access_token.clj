(ns org.bdinetwork.service-provider.authentication.access-token
  (:require [buddy.sign.jwt :as jwt]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as status])
  (:import java.time.Instant
           java.util.UUID))

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
  [{:keys [client-id server-id private-key access-token-ttl-seconds]}]
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
  [access-token {:keys [server-id public-key access-token-ttl-seconds]}]
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

(defn- get-bearer-token
  [request]
  (when-let [auth-header (-> request
                             (get-in [:headers "authorization"]))]
    (second (re-matches #"Bearer (\S+)" auth-header))))

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
    (if-let [access-token (get-bearer-token request)]
      ;; This (if-let [... (try ...)] ...) construct is messy.
      ;;
      ;; We want to capture exceptions thrown when parsing access
      ;; tokens but exceptions in (f request) should be left
      ;; alone.
      (if-let [client-id (try (access-token->client-id access-token opts)
                              (catch Exception e
                                (log/error "Invalid access token" e)
                                nil))]
        (f (assoc request :client-id client-id))
        invalid-token-response)
      (f request))))
