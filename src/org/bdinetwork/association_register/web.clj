;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.web
  (:require [compojure.core :refer [GET defroutes]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]
            [org.bdinetwork.service-provider.association :as association]
            [org.bdinetwork.service-provider.authentication :as authentication]
            [org.bdinetwork.ishare.jwt :as ishare.jwt]))

(defroutes routes
  (GET "/parties/:id" {:keys [params association]}
    {:body (association/party association (:id params))
     :token-key "party_token"})
  ;; TODO: trusted list
  (constantly
   (not-found "Resource not found")))

(defn wrap-association
  [handler ds]
  (fn [request]
    (handler (assoc request :association ds))))

(defn wrap-token-response
  [handler {:keys [private-key x5c server-id]}]
  (fn [{:keys [client-id] :as request}]
    (let [{:keys [body token-key] :as response} (handler request)]
      (if (and client-id token-key)
        (assoc response :body {token-key (ishare.jwt/make-jwt (assoc body
                                                                     :iss server-id
                                                                     :sub server-id ;; TODO: check this
                                                                     :aud client-id)
                                                              private-key
                                                              x5c)})
        response))))

(defn make-handler
  [association config]
  (-> routes
      (authentication/wrap-authentication config)
      (wrap-token-response config)
      (wrap-association association)
      (wrap-params)
      (wrap-json-response)))
