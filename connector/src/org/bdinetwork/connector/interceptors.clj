 ;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.authentication.client-assertion :as client-assertion]
            [org.bdinetwork.authentication.in-memory-association :refer [in-memory-association read-source]]
            [org.bdinetwork.authentication.remote-association :refer [remote-association]]
            [org.bdinetwork.connector.interceptors.audit-log :refer [audit-log-response]]
            [org.bdinetwork.ishare.client :as ishare-client]
            [org.bdinetwork.ishare.client.request :as ishare-request]
            [org.bdinetwork.ishare.client.validate-delegation :as validate-delegation]
            [passage.interceptors :as passage]
            [passage.response :as response]
            [ring.middleware.json :as ring-json]
            [ring.middleware.params :as ring-params])
  (:import java.time.Instant))

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
  Responds with 401 Unauthorized when request is not allowed.  Example:

  ```
  [(bdi/authenticate {:server-id   \"EU.EORI.CONNECTOR\"
                      :private-key #private-key \"certs/connector.key.pem\"
                      :public-key  #public-key \"certs/connector.cert.pem\"
                      :x5c         #x5c \"certs/connector.x5c.pem\"
                      :association-server-id  \"EU.EORI.ASSOCIATION-REGISTER\"
                      :association-server-url \"https://association-register.com\"})]
  ```"
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
  "Ensure the \"X-Bdi-Client-Id\" request header is **not** already set on a request for public endpoints which do not need authentication.

  This prevents clients from fooling the backend into being
  authenticated.  **Always use this on public routes when
  authentication is optional downstream.**"
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
  "Provide a token endpoint to provide access tokens for machine-to-machine (M2M) operations.

  Note: this interceptor does no matching.  Example:

  ```
  {:match        {:uri \"/connect/token\"}
   :interceptors
   [[bdi/connect-token {:server-id   \"EU.EORI.CONNECTOR\"
                        :private-key #private-key \"certs/connector.key.pem\"
                        :public-key  #public-key \"certs/connector.cert.pem\"
                        :x5c         #x5c \"certs/connector.x5c.pem\"
                        :association-server-id  \"EU.EORI.ASSOCIATION-REGISTER\"
                        :association-server-url \"https://association-register.com\"}]
    ..]}
  ```"
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
     {:pre [server-id x5c private-key association-server-id association-server-url dataspace-id]}
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



(def expires-in-fraction
  "Fraction of expires/max-age seconds to consider."
  90/100)

(defn- now-in-epoch-seconds
  []
  (.getEpochSecond (Instant/now)))

(defn- deferred-cached
  "Deferred cache of `c` for key `k` and `:payload` of `f`.
  Returned `:exp` from `f` is epoch seconds of expiration.

  Note: this cache does not evict entries, it only validates it
  expiration date, so the assumption the amount of `k`s is limited."
  [c k f]
  {:pre [c]}
  (d/let-flow
      [{:keys [payload]}
       (-> (swap! c update k
                  (fn [slot]
                    (if slot
                      (d/let-flow [{:keys [exp]} slot]
                        (if (< (now-in-epoch-seconds) exp)
                          slot
                          (f)))
                      (f))))
           (get k))]
    payload))

(defn- get-bearer-token-cache-slot
  "Return a non blocking future of `{:payload \"token\", :exp 1234}` for arguments."
  [{:keys [server-id base-url client-id private-key x5c association-id association-url path]}]
  (future
    (let [res (-> {:ishare/server-id server-id
                   :ishare/base-url  base-url

                   ;; credentials
                   :ishare/client-id   client-id
                   :ishare/private-key private-key
                   :ishare/x5c         x5c

                   ;; for adherence test of server
                   :ishare/satellite-id  association-id
                   :ishare/satellite-base-url association-url}

                  (ishare-request/access-token-request path)
                  (ishare-client/exec))]
      {:payload (:ishare/result res)
       :exp     (+ (now-in-epoch-seconds)
                   (* expires-in-fraction
                      (-> res :body (get "expires_in"))))})))

(defn ^{:interceptor  true
        :expr-arglist '[{:keys [server-id base-url client-id private-key x5c association-id association-url path]}]}
  set-bearer-token
  "Set a bearer token on the current request for the given `server-id` and `base-url`.

  Example:

  ```
  [(bdi/set-bearer-token) {;; target server
                           :server-id       server-id
                           :base-url        server-url

                           ;; credentials
                           :client-id       server-id
                           :private-key     private-key
                           :x5c             x5c

                           ;; association to check server adherence
                           :association-url association-server-url
                           :association-id  association-server-id}]
  ```

  The `:path` can be added for a non-standard token endpoint location,
  otherwise `/connect/token` is used."
  []
  (let [cache (atom {})]
    {:enter
     (fn set-bearer-token-enter
       [ctx
        {:keys [server-id base-url
                client-id private-key x5c
                association-id association-url]
         :as config}]
       {:pre [client-id private-key x5c base-url server-id association-id association-url]}
       (d/let-flow
           [token (deferred-cached cache [server-id base-url client-id association-id]
                    #(get-bearer-token-cache-slot config))]
         (assoc-in ctx [:request :headers "authorization"]
                   (str "Bearer " token))))}))


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

(def ^{:interceptor  true
       :expr-arglist '[{:keys [policy-issuer resource-type resource-identifier resource-attribute action]}]}
  noodlebar-validate-policy
  "Retrieves and evaluates delegation evidence for request.
  Responds with 403 Forbidden when the evidence is not found or does
  not match the delegation mask.

  Derives some information from the request's Bearer token claims:

  The policy's target must match the bearer-token's :organizationId claim
  The policy's service-provider must match the :aud claim

  Required
"
  (update noodlebar-delegation :enter
          (fn noodlebar-policy-enter [enter]
            (fn [ctx base-request {:keys [policy-issuer resource-type resource-identifier resource-attribute action]}]
              (enter ctx
                     base-request
                     {:policyIssuer policy-issuer
                      :target       {:accessSubject (get-in ctx [:oauth2/bearer-token-claims :organizationId])}
                      :policySets
                      [{:policies
                        [{:rules [{:effect "Permit"}]
                          :target
                          {:resource {:type        (or resource-type "")
                                      :identifiers [resource-identifier]
                                      :attributes  [resource-attribute]}
                           :actions  [action]
                           :environment
                           {:serviceProviders [(get-in ctx [:oauth2/bearer-token-claims :aud])]}}}]}]})))))




(def ^{:interceptor  true
       :expr-arglist '[& [additional-props]]}
  logger
  "Log incoming requests, response status and duration.

  Also logs BDI specific information: \"client\",
  \"delegation-evidence\", \"delegation-issues\", \"delegation-mask\".
 
  Optional `additional-props` will be evaluated in the \"leave\"
  phase and logged as diagnostic context, `props` should be a shallow
  map with string keys.

  Example log messsage:

  ```
  GET http://localhost:8081/ HTTP/1.1 / 200 OK / 370ms
  ```

  Example with MDC:

  ```edn
  [bdi/logger {\"ua\" (get-in request [:headers \"user-agent\"])}]
  ```

  Example log message:

  ```
  GET http://localhost:8080/ HTTP/1.1 / 200 OK / 123ms status=200, uri=\"/\", ua=\"curl/1.2.3\"
  ```"
  (-> passage/logger
      (update :leave
              (fn [leave]
                (fn logger-leave
                  ([{:keys [request response] :as ctx} additional-props]
                   (leave (merge {"uri"                 (get request :uri)
                                  "status"              (get response :status)
                                  "client"              (get-in ctx [:oauth2/bearer-token-claims :sub])
                                  "delegation-issues"   (get ctx :delegation-issues)
                                  "delegation-evidence" (get ctx :delegation-evidence)
                                  "delegation-mask"     (get ctx :delegation-mask)}
                                 additional-props)))
                  ([ctx]
                   (logger-leave ctx nil)))))))
