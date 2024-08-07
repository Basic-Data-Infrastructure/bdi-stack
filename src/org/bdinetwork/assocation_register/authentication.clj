(ns org.bdinetwork.assocation-register.authentication
  (:require [clojure.core.cache :as cache]
            [clojure.string :as string]
            [nl.jomco.http-status-codes :as status]
            [org.bdinetwork.assocation-register.authentication.access-token :as access-token]
            [org.bdinetwork.assocation-register.authentication.x5c :as x5c]
            [org.bdinetwork.assocation-register.data-source :as data-source]
            [org.bdinetwork.ishare.jwt :as ishare.jwt]))

;; Client assertions may only be used once. We keep track of the
;; client assertions seen.
;;
;; We keep a TTL cache for client assertions -- ensure we will only
;; accept assertions once, based on their :jti.
;;
;; Note that client_assertions are only valid for 30 seconds + some
;; leeway, so we don't need to keep track of them for long.

(def jti-cache-ttl-seconds 120)

(defn mk-jti-cache-atom
  "Create an empty jti cache atom"
  []
  (atom {:impl (cache/ttl-cache-factory {} {:ttl (* 1000 jti-cache-ttl-seconds)})}))

(defn new-jti?!
  "Returns `true` if jti wasn't in cache. Updates cache if jti is new"
  [jti-cache-atom jti]
  (:latest-new? (swap! jti-cache-atom
                       (fn [{:keys [impl] :as cache}]
                         (if (cache/has? impl jti)
                           (assoc cache :latest-new? false)
                           (-> cache
                               (update :impl cache/miss jti true)
                               (assoc :latest-new? true)))))))

;; ishare token validation
;;
;; According to https://dev.ishare.eu/reference/authentication
;;
;; See also https://1961974616-files.gitbook.io/~/files/v0/b/gitbook-x-prod.appspot.com/o/spaces%2FhIVZwp4ZxhYhb39SlKH3%2Fuploads%2FqGr0dWPuez172U5Fo8IU%2F190501D_Access_token_validation.pdf?alt=media

(defn bad-request
  [msg]
  {:status status/bad-request
   :body msg})

(defn check-access-token-request
  [{:keys [request-method]
    {:strs [grant_type scope client_id client_assertion_type]} :params}]
  (cond
    (not= :post request-method)
    {:status status/method-not-allowed}

    (not= "client_credentials" grant_type)
    (bad-request "Invalid grant_type")

    (not= "iSHARE" scope)
    (bad-request "Invalid scope")

    (string/blank? client_id)
    (bad-request "Invalid client_id")

    (not= "urn:ietf:params:oauth:client-assertion-type:jwt-bearer" client_assertion_type)
    (bad-request "Invalid client_assertion_type")))

(defn client-assertion-response
  [{:keys                                [data-source]
    {:strs [client_id client_assertion]} :params :as request}
   {:keys [private-key server-id jti-cache-atom access-token-ttl-seconds]}]

  (or (check-access-token-request request)
      (try
        (let [{:keys [iat exp jti sub aud iss]} (ishare.jwt/unsign-token client_assertion)
              {:keys [x5c]}                     (ishare.jwt/decode-header client_assertion)
              client-cert                       (first x5c)]
          (cond
            (not= client_id sub iss)
            (bad-request "sub != client_id")

            (not (new-jti?! jti-cache-atom jti))
            (bad-request "Stale client_assertion")

            (not= aud server-id)
            (bad-request "Invalid audience claim")

            (not= (- exp 30) iat)
            (bad-request "Invalid expiry time")

            (not (x5c/validate-chain x5c data-source))
            (bad-request "Invalid certificate chain")

            :else
            (let [party (data-source/party data-source sub)]
              (cond
                (not party)
                (bad-request "Invalid client")

                (not (some #(= client-cert (get % "x5c")) (party "certificates")))
                ;; TODO: check start_time and end_time
                (bad-request "Incorrect party certificate")

                (not= "Active" (get-in party ["adherence" "status"]))
                (bad-request "Party not active")

                :else
                (let [token (access-token/mk-access-token {:client-id                (get party "party_id")
                                                           :server-id                server-id
                                                           :access-token-ttl-seconds access-token-ttl-seconds
                                                           :private-key              private-key})]
                  {:status status/ok
                   :body   {:access_token token
                            :token_type   "Bearer"
                            :expires_in   access-token-ttl-seconds}})))))
        (catch Exception e
          (bad-request (ex-message e))))))

(defn wrap-client-assertion
  [f opts]
  (let [jti-cache-atom (mk-jti-cache-atom)]
    (fn client-assertion-wrapper
      [{:keys [uri] :as request}]
      (if (= "/connect/token" uri)
        (client-assertion-response request (assoc opts :jti-cache-atom jti-cache-atom))
        (f request)))))

(defn wrap-authentication
  [f opts]
  (-> f
      (wrap-client-assertion opts)
      (access-token/wrap-access-token opts)))
