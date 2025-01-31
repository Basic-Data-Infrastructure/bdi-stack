;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.web
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [compojure.core :refer [defroutes POST]]
            [nl.jomco.http-status-codes :as status]
            [org.bdinetwork.authorization-register.delegations :as delegations]
            [org.bdinetwork.ring.diagnostic-context :refer [with-context wrap-request-context]]
            [org.bdinetwork.ishare.jwt :as ishare.jwt]
            [org.bdinetwork.ring.authentication :as authentication]
            [org.bdinetwork.ring.ishare-validator :as validator]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]))

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

(defn validate-delegation-evidence
  [delegation-evidence]
  (validator/validate delegation-evidence
                      ;; path to delegation evidence in iSHARE OpenAPI definition
                      ["components" "schemas" "jwt_payload_delegation_evidence_token" "properties" "delegationEvidence"]))

(defroutes routes
  (POST "/delegation"
      {:keys                       [policy-view client-id]
       {:strs [delegationRequest]} :params}
    (if-let [delegation-evidence (delegations/delegation-evidence policy-view delegationRequest)]
      (if-let [issues (validate-delegation-evidence delegation-evidence)]
        (throw (ex-info "Invalid delegation evidence constructed"
                        {:issues issues}))
        {:status    status/ok
         :body      {"delegationEvidence" delegation-evidence}
         :token-key :delegation_token})
      (do
        (log/warn "No policy found for" client-id (pr-str delegationRequest))
        {:status status/not-found})))
  (POST "/policy"
      {:keys [client-id policy-store params]}
    (if (= client-id (get-in params ["delegationEvidence" "policyIssuer"]))
      ;; TODO implement schema validations for all endpoints
      (if-let [issues (validate-delegation-evidence (get params "delegationEvidence"))]
        {:status status/bad-request
         :body   {:error  "Invalid delegation evidence"
                  :issues issues}}
        {:status    status/ok
         :body      {:policyId (delegations/delegate! policy-store (get params "delegationEvidence"))}
         :token-key :delegation_token})
      {:status status/forbidden
       :body   {:error "policyIssuer does not match client_id"}}))
  (constantly (not-found "Resource not found.")))

(defn wrap-association
  [f association]
  (fn [r]
    (f (assoc r :association association))))

(defn error-message-from-response
  [{:keys [status body exception]}]
  (if (<= 400 status)
    (if exception
      (str (ex-message exception) " " (ex-data exception))
      (or (:message body)
          (let [msg (string/replace (str body) #"\s+" " ")]
            msg)))))

(defn wrap-log
  [handler]
  (fn [request]
    (try (let [{:keys [status body] :as response} (handler request)]
           (if (<= 400 status)
             (log/error (str (:status response) " " (:request-method request) " " (:uri request) " " (error-message-from-response response)))
             (log/info (str (:status response) " " (:request-method request) " " (:uri request))))
           response))))

(defn wrap-server-error
  [f]
  (fn [request]
    (try (f request)
         (catch Exception e
           {:status    500
            :exception e
            :body      {:error "Internal server error"}}))))

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
      (wrap-server-error)
      (wrap-json-response)
      (wrap-json-params {:key-fn identity})
      (wrap-params)
      (wrap-log)
      (wrap-request-context)))
