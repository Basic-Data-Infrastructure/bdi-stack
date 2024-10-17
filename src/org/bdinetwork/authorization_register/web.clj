;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.web
  (:require [compojure.core :refer [defroutes GET POST]]
            [nl.jomco.http-status-codes :as status]
            [org.bdinetwork.authorization-register.delegations :as delegations]
            [org.bdinetwork.ishare.jwt :as ishare.jwt]
            [org.bdinetwork.service-provider.authentication :as authentication]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.util.response :refer [not-found]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.tools.logging :as log]))

(defn wrap-token-response
  [handler {:keys [private-key x5c server-id]}]
  (fn [{:keys [client-id] :as request}]
    (let [{:keys [body token-key] :as response} (handler request)]
      (if token-key
        (do
          (assert client-id
                  "Can't sign response without a client id")
          (assoc response :body
                 {token-key (ishare.jwt/make-jwt (assoc body
                                                        :iss server-id
                                                        :sub server-id
                                                        :aud client-id)
                                                 private-key
                                                 x5c)}))
        response))))

;; https://dev.ishare.eu/reference/delegation-mask

(defn wrap-policies
  [f policy-store policy-view]
  (fn [request]
    (f (assoc request
              :policy-store policy-store
              :policy-view policy-view))))

(defroutes routes
  (POST "/delegation"
      {:keys                       [client-id
                                    policy-view]
       {:strs [delegationRequest]} :params}
    {:status    status/ok
     :body      {"delegationEvidence"
                 (delegations/delegation-evidence policy-view delegationRequest)}
     :token-key :delegation_token})
  (POST "/policy"
      {:keys                        [client-id
                                     policy-store
                                     params]}
    (if (= client-id (get-in params ["delegationEvidence" "policyIssuer"]))
      {:status    status/ok
       :body      {:policyId (str (delegations/delegate! policy-store (get params "delegationEvidence")))}
       :token-key :delegation_token}
      {:status    status/forbidden
       :body      {:error "policyIssuer does not match client_id"}}))
  (constantly (not-found "Resource not found.")))

(defn wrap-association
  [f association]
  (fn [r]
    (f (assoc r :association association))))

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

(defn wrap-private-api
  [f]
  (fn private-api-wrapper [{:keys [client-id] :as request}]
    (if client-id
      (f request)
      {:status status/unauthorized})))

(defn mk-app
  [{:keys [policy-store policy-view association]} config]
  {:pre [association policy-store policy-view]}
  (-> routes
      (wrap-policies policy-store policy-view)
      (wrap-token-response config)
      (wrap-private-api)
      (authentication/wrap-authentication config)
      (wrap-association association)
      (wrap-json-response)
      (wrap-json-params {:key-fn identity})
      (wrap-params)
      (wrap-log)))
