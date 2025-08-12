;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.noodlebar.request
  (:require [clojure.core.memoize :as memoize]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.cache :as cache]))

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

(defn- get-bearer-token* [req]
  (client/exec (access-token-request req)))

(def get-bearer-token
  (memoize/memoizer get-bearer-token*
                    (cache/expires-cache-factory (comp cache/bearer-token-expires-at deref))))

(defn coremanager-request
  [{:keys [coremanager-url] :as req}]
  {:pre [coremanager-url]}
  (assoc req
         :ishare/server-adherent? true
         :ishare/base-url coremanager-url
         :ishare/bearer-token (:ishare/result (get-bearer-token req))
         :as :json))

(defn organisation-request
  [req organisation-id]
  (-> req
      (coremanager-request)
      (assoc :method :get
             :path (str "organization-registry/" organisation-id)
             :ishare/lens [:body])))

(defn unsigned-delegation-request
  [req delegation-mask]
  (-> req
      (coremanager-request)
      (assoc :method :post
             :path "authorization/unsigned-delegation"
             :json-params {:delegationRequest delegation-mask}
             :ishare/lens [:body])))

(comment
  (-> {:coremanager-url      (System/getenv "NOODLEBAR_COREMANAGER_URL")
       :oauth2/token-url     (System/getenv "NOODLEBAR_TOKEN_URL")
       :oauth2/audience      (System/getenv "NOODLEBAR_AUDIENCE")
       :oauth2/client-id     (System/getenv "NOODLEBAR_CLIENT_ID")
       :oauth2/client-secret (System/getenv "NOODLEBAR_CLIENT_SECRET")}
      (unsigned-delegation-request {:policyIssuer "CGI (331360040018)" :target {:accessSubject "(Poort8)"} :policySets [{ :policies [{:rules [{:effect "Permit"}] :target {:resource {:type "" :identifiers ["policies"] :attributes [""]} :actions ["read"] :environment {:serviceProviders ["CGI (331360040018)"]}}}]}]})
      (client/exec))
  )
