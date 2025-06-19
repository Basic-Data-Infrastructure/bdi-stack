(ns org.bdinetwork.oauth2.request 
  (:require
   [org.bdinetwork.ishare.client :as client]))

;; TODO: refactor ishare/client into bdi client, with ishare-specific
;; request/middleware and oauth2 parts.

(defn access-token-request
  "Create machine-to-machine access token request."
  [{:oauth2/keys [token-url audience client-id client-secret] :as req}]
  {:pre [token-url audience client-id client-secret]}
  (assoc req
         :method :post
         :url token-url
         :as :json
         :json-params {"client_id" client-id
                       "client_secret" client-secret
                       "audience" audience
                       "grant_type" "client_credentials"}
         ;; disable ishare middleware for this request
         :ishare/server-adherent? true
         :ishare/bearer-token nil

         :ishare/lens [:body "access_token"]))

(defn coremanager-request
  [{:keys [coremanager-url] :as req}]
  {:pre [coremanager-url]}
  (assoc req
         :ishare/server-adherent? true
         :ishare/base-url coremanager-url
         :ishare/bearer-token (:ishare/result (client/exec (access-token-request req)))
         :as :json))

(defn organisation-request
  [req organisation-id]
  (-> req
      (coremanager-request)
      (assoc :method :get
             :path (str "organization-registry/" organisation-id)
             :ishare/lens [:body])))

;;; noodlebar sp authenticatie
;;;
;;;
;;; NB authenticatie gaat via oauth2 server (nu Auth0)
;;;
;;; vanuit SP perspectief:
;;;
;;; - receive access token (bearer token = signed JWT)
;;; - validate JWT (via buddy.jwt/unsign + web keyset)
;;;
;;;   https://auth0.com/docs/secure/tokens/access-tokens/validate-access-tokens
;;;   web keys: https://auth0.com/docs/secure/tokens/json-web-tokens/json-web-key-sets
;;;             https://gist.github.com/ggeoffrey/b72ed568be4914a990790ea6b09c2c66
;;;
;;;  Zie ook org.bdinetwork.ishare.jwt/unsign-token, daar ontbreekt
;;;  "alleen" nog het JWKS stuk voor ophalen van huidige public key
;;;
;;; - haal de party-id claims uit de JWT en check dat deze lid zijn van de associatie
;;;
;;; hierna: autorisatie via AR van noodlebar
