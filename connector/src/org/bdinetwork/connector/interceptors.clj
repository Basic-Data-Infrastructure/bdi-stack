;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
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
            [org.bdinetwork.ishare.client.validate-delegation :as validate-delegation]
            [passage.response :as response]
            [ring.middleware.json :as ring-json]
            [ring.middleware.params :as ring-params]))

(defn extract-client-id [request config]
  (let [auth (get-in request [:headers "authorization"])]
    (when-let [[_ token] (and auth (re-matches #"Bearer (\S+)" auth))]
      (try
        (access-token/access-token->client-id token config)
        (catch Exception e
          (log/infof "Invalid access token: %s" (ex-message e))
          nil)))))

(defn ^{:interceptor true} authenticate
  "Enforce BDI authentication on incoming requests and add \"x-bdi-client-id\" request header.
  Responds with 401 Unauthorized when request is not allowed."
  [config]
  {:enter
   (fn authenticate-enter [{:keys [request] :as ctx}]
     (if-let [client-id (extract-client-id request config)]
       (-> ctx
           (assoc-in [:request :headers "x-bdi-client-id"] client-id)
           (assoc-in [:vars 'x-bdi-client-id] client-id))
       (assoc ctx :response
              {:status  http-status/unauthorized
               :headers {"www-authenticate" "Bearer scope=\"BDI\""}})))})

(def ^{:interceptor true} deauthenticate
  "Remove \"x-bdi-client-id\" request header for avoid clients from fooling backend into being authenticated."
  {:enter  (fn bdi-deauthenticate-enter [ctx]
             (update-in ctx [:request :headers] dissoc "x-bdi-client-id"))})

(defn- ->association
  "Setup an association interface from the given `config`."
  [{:keys [in-memory-association-data-source
           server-id x5c private-key association-server-id association-server-url]}]
  (if in-memory-association-data-source
    (in-memory-association (read-source in-memory-association-data-source))
    (do
      (assert [server-id x5c private-key association-server-id association-server-url])
      (remote-association #:ishare {:client-id          server-id
                                    :x5c                x5c
                                    :private-key        private-key
                                    :satellite-id       association-server-id
                                    :satellite-base-url association-server-url}))))

(defn client-assertion-response [{:keys [association] :as config} request]
  (-> request
      (ring-params/params-request)
      (assoc :association association)
      (client-assertion/client-assertion-response config)
      (ring-json/json-response {})))

(defn ^{:interceptor true} connect-token
  "Provide a access token (M2M) endpoint to acquire an authentication token.
  Note: this interceptor does not match on an `uri`, use a `:match` in
  the rules for that."
  [config]
  (let [jti-cache-atom (client-assertion/mk-jti-cache-atom)
        config         (assoc config
                              :jti-cache-atom jti-cache-atom
                              :association (->association config))]
    {:enter (fn bdi-connect-token-enter [{:keys [request] :as ctx}]
              (assoc ctx :response (client-assertion-response config request)))}))



(def ^{:interceptor true}
  delegation
  "Retrieves and evaluates delegation evidence for request.
  Responds with 403 Forbidden when the evidence is not found or does
  not match the delegation mask."
  {:enter
   (fn delegation-enter
     [ctx {:keys [server-id x5c private-key association-server-id association-server-url dataspace-id] :as _config} mask]
     (assert [server-id x5c private-key association-server-id association-server-url dataspace-id])
     (let [base-request {:ishare/satellite-base-url association-server-url
                         :ishare/satellite-id       association-server-id
                         :ishare/x5c                x5c
                         :ishare/client-id          server-id
                         :ishare/private-key        private-key
                         :ishare/dataspace-id       dataspace-id
                         :throw                     false}
           evidence     (validate-delegation/fetch-delegation-evidence base-request mask)
           issues       (validate-delegation/delegation-mask-evidence-mismatch mask evidence)
           ctx          (assoc ctx
                           :delegation-evidence evidence
                           :delegation-mask mask
                           :delegation-issues issues)]
       (cond-> ctx
         issues
         (assoc :response (-> response/forbidden
                              (assoc-in [:headers "content-type"] "application/json")
                              (assoc :body (json/json-str {:delegation-issues issues})))))))})



(defn ^{:interceptor true} demo-audit-log
  "Provide access to the last `:n-of-lines` (defaults to 100) lines of `:json-file` (required) and render them in a HTML table."
  [{:keys [json-file] :as opts}]
  {:pre [json-file]}
  {:enter (fn demo-audit-log-enter [ctx & _]
            (assoc ctx :response (audit-log-response opts)))} )



;; https://tsl-dataspace-coremanager.azurewebsites.net/scalar/#tag/authorization/POST/api/authorization/unsigned-delegation

(def ^{:interceptor true}
  noodlebar-delegation
  "Retrieves and evaluates delegation evidence for request.
  Responds with 403 Forbidden when the evidence is not found or does
  not match the delegation mask."
  {:enter
   (fn noodlebar-delegation-enter
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
                              (assoc :body (json/json-str {:delegation-issues issues})))))))})
