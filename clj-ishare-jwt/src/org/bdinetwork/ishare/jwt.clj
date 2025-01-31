;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.jwt
  "Create, sign and unsign (validate) iSHARE JWTs.

  See also: https://dev.ishare.eu/reference/ishare-jwt"
  (:require [buddy.core.keys :as keys]
            [buddy.sign.jwt :as jwt]
            [clojure.spec.alpha :as s])
  (:import (java.io StringReader)
           (java.time Instant)
           (java.util UUID)))

;; Data specs; these are used to validate the data shape.
;;
;; Since assertions and conditions can be disabled, public methods and
;; code directly handling external input MUST use other methods to
;; ensure input data is valid. See `check!` below.

(defn- check!
  "Check that `x` is valid for spec `spec-key`. Returns `x` if valid.

  Raises an exception if `x` is invalid. Unlike `s/assert` this cannot
  be disabled."
  [spec-key x]
  (when-let [data (s/explain-data spec-key x)]
    (throw (ex-info (s/explain-str spec-key x)
                    {:spec    spec-key
                     :x       x
                     :explain data})))
  x)


;; iSHARE JWT Header data specification

;; From https://dev.ishareworks.org/reference/jwt.html#jwt-header
;;
;;
;;  "Signed JWTs MUST use and specify the RS256 algorithm in the
;;   alg header parameter.
;;
;;   Signed JWTs MUST contain an array of the complete certificate
;;   chain that should be used for validating the JWT’s signature in
;;   the x5c header parameter up until an Issuing CA is listed from
;;   the iSHARE Trusted List.
;;
;;   Certificates MUST be formatted as base64 encoded PEM.
;;
;;   The certificate of the client MUST be the first in the array, the
;;   root certificate MUST be the last.
;;
;;   Except from the alg, typ and x5c parameter, the JWT header SHALL
;;   NOT contain other header parameters."

(s/def ::typ #{"JWT"})
(s/def ::alg #{:rs256 :rs512}) ;; we'll also allow RS512 since it is used in practice
(s/def ::base64-str
  (s/and string?
         #(re-matches #"[A-Za-z0-9\+/=]+" %)))
(s/def ::cert-str ::base64-str)
(s/def ::x5c (s/coll-of ::cert-str :kind vector? :min-count 1))

(defn- no-additional-keys?
  "True if m has no keys but the keys in `ks`."
  [m ks]
  (and (map? m)
       (every? (set ks) (keys m))))

(s/def ::header
  (s/and
     (s/keys :req-un [::typ ::alg ::x5c])
     #(no-additional-keys? % [:typ :alg :x5c])))


;; iSHARE JWT payload data specs

;; FEEDBACK: the following is incorrect: iss and sub are not always
;; equal and may not be client-id. For instance, delegation evidence
;; is issued by AR server for client.

;; From https://dev.ishareworks.org/reference/jwt.html#jwt-payload
;;
;;   "The JWT payload MUST conform to the private_key_jwt method as
;;    specified in OpenID Connect 1.0 Chapter 9.
;;
;;    The JWT MUST always contain the iat claim.
;;
;;    The iss and sub claims MUST contain the valid iSHARE
;;    identifier (EORI) of the client.
;;
;;    The aud claim MUST contain only the valid iSHARE identifier of
;;    the server. Including multiple audiences creates a risk of
;;    impersonation and is therefore not allowed.
;;
;;    The JWT MUST be set to expire in 30 seconds. The combination of
;;    iat and exp claims MUST reflect that. Both iat and exp MUST be
;;    in seconds, NOT milliseconds. See UTC Time formatting for
;;    requirements.
;;
;;    The JWT MUST contain the jti claim for audit trail purposes. The
;;    jti is not necessary a GUID/UUID.
;;
;;     Depending on the use of the JWT other JWT payload data MAY be
;;     defined."

;; From  OpenID Connect 1.0 Chapter 9.
;; https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication
;;
;; private_key_jwt Clients that have registered a public key sign a
;; JWT using that key. The Client authenticates in accordance with
;; JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication
;; and Authorization Grants [OAuth.JWT] and Assertion Framework for
;; OAuth 2.0 Client Authentication and Authorization
;; Grants [OAuth.Assertions]. The JWT MUST contain the following
;; REQUIRED Claim Values and MAY contain the following OPTIONAL Claim
;; Values:
;;
;;     iss
;;
;;         REQUIRED. Issuer. This MUST contain the client_id of the
;;         OAuth Client.
;;
;;     sub
;;
;;         REQUIRED. Subject. This MUST contain the client_id of the
;;         OAuth Client.
;;
;;     aud
;;
;;         REQUIRED. Audience. The aud (audience) Claim. Value that
;;         identifies the Authorization Server as an intended
;;         audience. The Authorization Server MUST verify that it is
;;         an intended audience for the token. The Audience SHOULD be
;;         the URL of the Authorization Server's Token Endpoint.
;;
;;     jti
;;
;;         REQUIRED. JWT ID. A unique identifier for the token, which
;;         can be used to prevent reuse of the token. These tokens
;;         MUST only be used once, unless conditions for reuse were
;;         negotiated between the parties; any such negotiation is
;;         beyond the scope of this specification.
;;
;;     exp
;;
;;         REQUIRED. Expiration time on or after which the JWT MUST
;;         NOT be accepted for processing.
;;
;;     iat
;;
;;         OPTIONAL. Time at which the JWT was issued.

(s/def ::signed-token
  (s/and string?
         seq))

(s/def ::timestamp-seconds
  integer?)
(s/def ::iat ::timestamp-seconds)
(s/def ::exp ::timestamp-seconds)
(s/def ::nbf ::timestamp-seconds)

(s/def ::ishare-identifier
  (s/and string?
         #(re-matches #"EU\.EORI\..*" %)))

(s/def ::iss ::ishare-identifier)
(s/def ::sub ::ishare-identifier)
(s/def ::aud ::ishare-identifier)

(s/def ::jti (s/and string? seq))

(defn- expires-in-30-seconds?
  [{:keys [iat exp]}]
  (= 30 (- exp iat)))

(defn- nbf-equal-to-iat?
  [{:keys [nbf iat] :as payload}]
  ;; nbf is optional, only do the check if nbf is present
  (or (not (contains? payload :nbf))
      (= nbf iat)))

(s/def ::payload
  (s/and (s/keys :req-un [::iss ::sub ::aud ::jti ::iat ::exp]
                 :opt-un [::nbf])
         expires-in-30-seconds?
         nbf-equal-to-iat?))

(defn- iss-equal-to-sub?
  [{:keys [iss sub]}]
  (= iss sub))


(s/def ::client-assertion-payload
  (s/and ::payload
         iss-equal-to-sub?))


;; Parsing and validating iSHARE JWTs

(defn- cert-reader
  "Convert base64 encoded certificate string into a reader for parsing as a PEM."
  [cert-str]
  (check! ::cert-str cert-str)
  (StringReader. (str "-----BEGIN CERTIFICATE-----\n"
                      cert-str
                      "\n-----END CERTIFICATE-----\n")))

(defn decode-header
  "Return header info from a signed JWT. Does not validate."
  [token]
  (check! ::header (jwt/decode-header token)))

(defn x5c->first-public-key
  "Extract first public-key from x5c header."
  [x5c]
  (keys/public-key (cert-reader (first x5c))))

(defn- unsign*
  [token]
  (check! ::signed-token token)
  (let [{:keys [x5c alg]} (decode-header token)
        pkey          (x5c->first-public-key x5c)]
    (when-not (#{:rs512 :rs256} alg)
      (throw (ex-info "Invalid JWT alg" {:alg alg})))
    (jwt/unsign token pkey {:alg alg :leeway 5})))

(defn unsign-token
  "Parse a signed token. Returns parsed data or raises exception.

  Raises an exception when token is not a valid iSHARE JWT for any
  reason, including expiration.

  Does not check revocation status of certificates."
  [token]
  (check! ::payload (unsign* token)))

(defn unsign-client-assertion
  "Parse a signed client assertion. Returns parsed data or raises exception.

  Raises an exception when client-assertion is not valid for any
  reason including expiration.

  Does not check revocation status of certificates."
  [client-assertion]
  (check! ::client-assertion-payload (unsign* client-assertion)))


;; Creating iSHARE JWTs

(defn- seconds-since-unix-epoch
  "Current number of seconds since the UNIX epoch."
  []
  (.getEpochSecond (Instant/now)))

(defn make-jwt
  "Generate JWT with provided `claims`, signed with `private-key`.

  The JWT header is set with :alg as RS256, :typ \"JWT\" and `:x5c`
  from `x5c` (a vector of certificate strings). The first certificate
  in `x5c` should correspond to `private-key`.

  A few claims are generated if not provided:

  - `:iat` -- the current time
  - `:exp` -- iat + 30 seconds
  - `:jti` -- a random UUID
  - `:nbf` -- iat

  https://dev.ishare.eu/reference/ishare-jwt"
  [{:keys [iat iss sub aud]
    :or   {iat (seconds-since-unix-epoch)}
    :as   claims}
   private-key x5c]
  {:pre [iss sub aud]}
  (jwt/sign (cond-> claims
              (not (contains? claims :jti))
              (assoc :jti (UUID/randomUUID))

              (not (contains? claims :iat))
              (assoc :iat iat)

              (not (contains? claims :exp))
              (assoc :exp (+ iat 30))

              ;; nbf is not required according to the spec, but the
              ;; Poort8 AR used to require it
              (not (contains? claims :nbf))
              (assoc :nbf iat))
            private-key
            {:alg    :rs256
             :header {:x5c x5c
                      :typ "JWT"}}))

(defn make-client-assertion
  "Create a signed client assertion for requesting an access token.

  See https://dev.ishare.eu/reference/ishare-jwt"
  [{:ishare/keys [client-id server-id x5c private-key]}]
  {:pre [client-id server-id x5c private-key]}
  (make-jwt {:iss client-id, :sub client-id, :aud server-id} private-key x5c))
