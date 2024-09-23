;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.web
  (:require [compojure.core :refer [GET defroutes]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]
            [nl.jomco.http-status-codes :as status]
            [org.bdinetwork.service-provider.association :as association]
            [org.bdinetwork.service-provider.authentication :as authentication]
            [org.bdinetwork.ishare.jwt :as ishare.jwt]
            [clojure.tools.logging :as log]))

(defroutes routes
  (GET "/parties/:id" {:keys [params association client-id]}
    (if client-id
      {:body {"party_info" (association/party association (:id params))}
       :token-key "party_token"}
      {:status status/unauthorized}))
  (GET "/trusted_list" {:keys [association client-id]}
    (if client-id
      {:body {"trusted_list" (association/trusted-list association)}
       :token-key "trusted_list_token"}
      {:status status/unauthorized}))
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

(defn wrap-log
  [handler]
  (fn [request]
    (try (let [response (handler request)]
           (log/info (str (:status response) " " (:request-method request) " " (:uri request)))
           response)
         (catch Exception e
           (log/error e
                      (str "Exception handling "  (:request-method request) " " (:uri request) ": " (ex-message e)))
           (throw e)))))


(defn make-handler
  [association config]
  (-> routes
      (wrap-token-response config)
      (authentication/wrap-authentication config)
      (wrap-association association)
      (wrap-json-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-log)))
