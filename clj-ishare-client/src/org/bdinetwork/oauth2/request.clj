;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.oauth2.request
  (:require [org.bdinetwork.ishare.client :as client]))

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
