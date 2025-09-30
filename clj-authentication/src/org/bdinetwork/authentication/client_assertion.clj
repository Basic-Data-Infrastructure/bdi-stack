;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication.client-assertion
  (:require [clojure.core.cache :as cache]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as status]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.authentication.association :as association]
            [org.bdinetwork.authentication.x5c :as x5c]
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
  "Create an empty jti cache atom."
  []
  (atom {:impl (cache/ttl-cache-factory {} {:ttl (* 1000 jti-cache-ttl-seconds)})}))

(defn new-jti?!
  "Returns `true` if jti wasn't in cache. Updates cache if jti is new."
  [jti-cache-atom jti]
  (:latest-new? (swap! jti-cache-atom
                       (fn [{:keys [impl] :as cache}]
                         (if (cache/has? impl jti)
                           (-> cache
                               (assoc :latest-new? false))
                           (-> cache
                               (update :impl cache/miss jti true)
                               (assoc :latest-new? true)))))))

;; ishare token validation
;;
;; According to https://dev.ishare.eu/reference/authentication
;;
;; See also https://1961974616-files.gitbook.io/~/files/v0/b/gitbook-x-prod.appspot.com/o/spaces%2FhIVZwp4ZxhYhb39SlKH3%2Fuploads%2FqGr0dWPuez172U5Fo8IU%2F190501D_Access_token_validation.pdf?alt=media

(defn bad-request
  [msg & [info]]
  {:status status/bad-request
   :body   (cond-> {:message msg}
             info (assoc :info info))})

(defn check-access-token-request
  [{:keys [request-method]
    {:strs [grant_type scope client_id client_assertion_type client_assertion]} :form-params}]
  (cond
    (not= :post request-method)
    {:status status/method-not-allowed}

    (not= "client_credentials" grant_type)
    (bad-request "Invalid grant_type"
                 {:grant_type grant_type})

    (not= "iSHARE" scope)
    (bad-request "Invalid scope"
                 {:scope scope})

    (string/blank? client_id)
    (bad-request "Invalid client_id"
                 {:client_id client_id})

    (not= "urn:ietf:params:oauth:client-assertion-type:jwt-bearer" client_assertion_type)
    (bad-request "Invalid client_assertion_type"
                 {:client_assertion_type client_assertion_type})

    (string/blank? client_assertion)
    (bad-request "Missing client_assertion")))

(defn client-assertion-response
  [{:keys                                [association]
    {:strs [client_id client_assertion]} :form-params :as request}
   {:keys [private-key server-id jti-cache-atom access-token-ttl-seconds]
    :or   {access-token-ttl-seconds access-token/default-access-token-ttl-seconds}}]
  (log/debug "Client assert response" {:client_assertion client_assertion})

  (or (check-access-token-request request)
      (try
        (let [{:keys [iat exp jti sub aud iss]} (ishare.jwt/unsign-token client_assertion)
              {:keys [x5c]}                     (ishare.jwt/decode-header client_assertion)
              client-cert                       (first x5c)]
          (cond
            (not= client_id sub)
            (bad-request "sub is not client_id"
                         {:client_id client_id
                          :sub       sub})

            (not= client_id iss)
            (bad-request "iss is not client_id"
                         {:client_id client_id
                          :iss       iss})

            (not (new-jti?! jti-cache-atom jti))
            (bad-request "Stale client_assertion"
                         {:jti jti})

            (not= aud server-id)
            (bad-request "Invalid audience claim"
                         {:aud       aud
                          :server-id server-id})

            (not= (- exp 30) iat)
            (bad-request "Invalid expiry time"
                         {:iat  iat
                          :exp  exp
                          :diff (- exp iat)})

            (<= (count x5c) 1)
            (bad-request "Incomplete certificate chain, x5c must provide full path from client certificate until trusted root" {:x5c x5c})

            (not (x5c/validate-chain x5c (association/trusted-list association)))
            (bad-request "Invalid certificate chain." {:x5c x5c})

            :else
            (let [party (association/party association client_id)]
              (cond
                (not party)
                (bad-request (str "Unknown client '" client_id "'")
                             {:client_id client_id})

                (not (some #(= client-cert (get % "x5c")) (party "certificates")))
                (bad-request (str "Incorrect client certificate for '" client_id "'")
                             {:client_id client_id})

                (not= "Active" (get-in party ["adherence" "status"]))
                (bad-request (str "Client '" client_id "' not active")
                             {:client_id client_id
                              :status    (get-in party ["adherence" "status"])})

                :else
                (let [token (access-token/mk-access-token {:client-id                client_id
                                                           :server-id                server-id
                                                           :access-token-ttl-seconds access-token-ttl-seconds
                                                           :private-key              private-key})]
                  {:status status/ok
                   :body   {:access_token token
                            :token_type   "Bearer"
                            :expires_in   access-token-ttl-seconds}})))))
        (catch clojure.lang.ExceptionInfo e
          (let [{:keys [type]} (ex-data e)]
            (if (= type :validation) ;; validation error in JWT
              (bad-request (ex-message e)
                           (ex-data e))
              (throw e)))))))
