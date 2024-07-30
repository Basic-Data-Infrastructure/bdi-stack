(ns org.bdinetwork.assocation-register.authentication.access-token
  (:require [buddy.sign.jwt :as jwt]
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
  (let [decoded (jwt/decode-header access-token)]
    (when (not= {:alg :rs256 :typ "JWT"} decoded)
      (throw (ex-info "Invalid JWT header" {:header decoded}))))
  (let [{:keys [iss sub aud iat nbf exp jti]} (jwt/unsign access-token public-key {:alg :rs256 :leeway 5})]
    (if (and (= iss server-id)
             (= aud server-id)
             (some? sub)
             (int? iat)
             (int? exp)
             (int? nbf)
             (= iat nbf)
             (= exp (+ iat access-token-ttl-seconds))
             (string? jti))
      sub
      (throw (ex-info "Invalid access token" {})))))

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
      (try
        (f (assoc request
                  :client-id (access-token->client-id access-token opts)))
        (catch Exception _
          ;; TODO log exception for debugging
          invalid-token-response))
      (f request))))
