;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.authentication.client-assertion :as client-assertion]
            [org.bdinetwork.authentication.in-memory-association :refer [in-memory-association read-source]]
            [org.bdinetwork.authentication.remote-association :refer [remote-association]]
            [org.bdinetwork.connector.interceptors.audit-log :refer [audit-log-response]]
            [org.bdinetwork.gateway.interceptors :refer [->interceptor interceptor]]
            [org.bdinetwork.gateway.response :as response]
            [org.bdinetwork.ishare.client.validate-delegation :as validate-delegation]
            [ring.middleware.json :as ring-json]
            [ring.middleware.params :as ring-params]))

(defn extract-client-id [request config]
  (let [auth (get-in request [:headers "authorization"])]
    (when-let [[_ token] (and auth (re-matches #"Bearer (\S+)" auth))]
      (try
        (access-token/access-token->client-id token config)
        (catch Exception e
          (log/info e "Invalid access token")
          nil)))))

(defmethod ->interceptor 'bdi/authenticate
  [[id] {:keys [server-id] :as config}]
  (interceptor
   :name (str id " " server-id)
   :doc "Enforce BDI authentication on incoming requests and add
   \"x-bdi-client-id\" request header.  Responds with 401 Unauthorized
   when request is not allowed."
   :enter
   (fn bdi-authenticate-enter [{:keys [request] :as ctx}]
     (if-let [client-id (extract-client-id request config)]
       (-> ctx
           (assoc-in [:request :headers "x-bdi-client-id"] client-id)
           (assoc-in [:vars 'x-bdi-client-id] client-id))
       (assoc ctx :response
              {:status  http-status/unauthorized
               :headers {"www-authenticate" "Bearer scope=\"BDI\""}})))))

(defmethod ->interceptor 'bdi/deauthenticate
  [[id] _]
  (interceptor
   :name (str id)
   :doc "Remove \"x-bdi-client-id\" request header for avoid clients
   from fooling backend into being authenticated."
   :enter
   (fn bdi-deauthenticate-enter [ctx]
     (update-in ctx [:request :headers] dissoc "x-bdi-client-id"))))

(defn ->association
  "Setup an association interface from the given `config`."
  [{:keys [in-memory-association-data-source
           server-id x5c private-key association-server-id association-server-url]}]
  (if in-memory-association-data-source
    (in-memory-association (read-source in-memory-association-data-source))
    (remote-association #:ishare {:client-id          server-id
                                  :x5c                x5c
                                  :private-key        private-key
                                  :satellite-id       association-server-id
                                  :satellite-base-url association-server-url})))

(defn client-assertion-response [{:keys [association] :as config} request]
  (-> request
      (ring-params/params-request)
      (assoc :association association)
      (client-assertion/client-assertion-response config)
      (ring-json/json-response {})))

(defmethod ->interceptor 'bdi/connect-token
  [[id] {:keys [server-id] :as config}]
  (interceptor
   :name (str id " " server-id)
   :doc "Provide a access token (M2M) endpoint to acquire a
   authentication token.  Note: this interceptor does not match on an
   `uri`, use a `:match` in the rules for that."
   :enter
   (let [jti-cache-atom (client-assertion/mk-jti-cache-atom)
         config         (assoc config
                               :jti-cache-atom jti-cache-atom
                               :association (->association config))]
     (fn bdi-connect-token-enter [{:keys [request] :as ctx}]
       (assoc ctx :response (client-assertion-response config request))))))



(defmethod ->interceptor 'demo/audit-log
  [[id {:keys [json-file] :as opts}] _]
  {:pre [json-file]}
  (interceptor
   :name (str id " " (pr-str opts))
   :doc "Provide access to the last `:n-of-lines` (defaults to 100)
   lines of `:json-file` (required) and render them in a HTML table."
   :enter
   (fn demo-audit-log-enter [ctx]
     (assoc ctx :response (audit-log-response opts)))))



(defmethod  ->interceptor 'noodlebar/delegation
  [[id base-request-expr mask-expr] & _]
  (interceptor
   :name (str id " delegation-chain")
   :args [base-request-expr mask-expr]
   :doc "Retrieves and evaluates delegation evidence for
   request. Responds with 403 Forbidden when the evidence is not found
   or does not match the delegation mask."
   :enter
   (fn delegation-chain-enter
     [ctx base-request mask]
     (let [evidence (validate-delegation/noodlebar-fetch-delegation-evidence base-request mask)
           issues   (validate-delegation/delegation-mask-evidence-mismatch mask evidence)
           ctx      (assoc ctx
                           :delegation-evidence evidence
                           :delegation-mask mask
                           :delegation-issues issues)]
       (cond-> ctx
         issues
         (assoc :response (-> response/forbidden
                              (assoc-in [:headers "content-type"] "application/json")
                              (assoc :body (json/json-str {:delegation-issues issues})))))))))
