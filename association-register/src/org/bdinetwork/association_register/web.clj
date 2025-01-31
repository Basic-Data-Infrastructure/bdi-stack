;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.web
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes GET]]
            [clojure.string :as string]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.ishare.jwt :as ishare.jwt]
            [org.bdinetwork.ring.association :as association]
            [org.bdinetwork.ring.authentication :as authentication]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]))

(defroutes routes
  (GET "/parties/:id" {:keys [params association client-id]}
    (if client-id
      {:body      {"party_info" (association/party association (:id params))}
       :token-key "party_token"}
      {:status http-status/unauthorized}))
  ;; For backwards compatibility, also implement /parties endpoint
  ;; with params, but only for queries that provide an EORI -- meaning
  ;; it's only possible to fetch info for a single party.
  (GET "/parties"  {:keys          [association client-id]
                    {:strs [eori]} :query-params}
    (if client-id
      (if eori
        (let [party (association/party association eori)]
          ;; FIXME: check certificate subject name
          {:body      {"parties_info" {"count" 0
                                       "data"  [party]}}
           :token-key "parties_token"})
        {:status http-status/bad-request
         :body   {:error "parties request without eori param is not supported"}})
      {:status http-status/unauthorized}))
  (GET "/trusted_list" {:keys [association client-id]}
    (if client-id
      {:body      {"trusted_list" (association/trusted-list association)}
       :token-key "trusted_list_token"}
      {:status http-status/unauthorized}))
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
                                                                     :sub server-id
                                                                     :aud client-id)
                                                              private-key
                                                              x5c)})
        response))))

(defn error-message-from-response
  [{:keys [status body]}]
  (if (<= 400 status)
    (or (:message body)
        (let [msg (string/replace (str body) #"\s+" " ")]
          (if (<= 300 (count msg))
            (subs msg 0 300)
            msg)))))

(defn wrap-log
  [handler]
  (fn [request]
    (try (let [{:keys [status body] :as response} (handler request)]
           (if (<= 500 status)
             (log/error (str (:status response) " " (:request-method request) " " (:uri request)))
             (log/info (str (:status response) " " (:request-method request) " " (:uri request))))
           (when-let [msg (error-message-from-response response)]
             (log/error msg))
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
