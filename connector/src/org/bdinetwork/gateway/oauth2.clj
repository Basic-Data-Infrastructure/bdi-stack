;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.oauth2
  (:require [aleph.http :as http]
            [buddy.core.keys :as keys]
            [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [nl.jomco.http-status-codes :as http-status])
  (:import java.time.Instant
           java.util.Base64))

(defn- guard-status-ok [{:keys [status] :as res}]
  (when-not (= http-status/ok status)
    (throw (ex-info (str "Unexpected status (" status ")") {:status status})))
  res)

(def default-jwks-max-age 15)

(defn- cache-control-extract-exp
  [{{:strs [cache-control]} :headers}]
  (+ (.getEpochSecond (Instant/now))
     (if-let [[_ max-age] (and cache-control
                               (re-find #"max-age=(\d+)" cache-control))]
       (Long/parseLong max-age)
       default-jwks-max-age)))

(defn- decode-payload
  "Decode payload without validation."
  [token]
  (let [[_ payload _] (some-> token (string/split #"\." 3))
        decoder (Base64/getUrlDecoder)]
    (-> (.decode decoder (.getBytes payload "UTF-8"))
        (String. "UTF-8")
        (json/read-str :key-fn keyword))))


(defn- now
  []
  (.getEpochSecond (Instant/now)))

(defn- deferred-cached
  "Cache deferred function.

  `miss-fn` returns a potentially deferred slot containing :exp
  and :payload, which is cached in `cache-atom`.

  Returns a deferred payload"
  [cache-atom ks miss-fn]
  {:pre [cache-atom]}
  (d/let-flow [{:keys [payload]} (-> (swap! cache-atom update-in ks
                                            (fn [slot]
                                              (if slot
                                                (d/let-flow [{:keys [exp]} slot]
                                                  (if (< (now) exp)
                                                    slot
                                                    (miss-fn)))
                                                (miss-fn))))
                                     (get-in (conj ks)))]
    payload))

(defn- fetch-openid-configuration
  "Fetch openid-configuration for `iss`."
  [iss {:keys [jwks-cache-atom] :as _opts}]
  {:pre [jwks-cache-atom]}
  (deferred-cached jwks-cache-atom [:openid-configuration iss]
    (fn []
      (d/let-flow [url (str iss (if (.endsWith iss "/") "" "/") ".well-known/openid-configuration")
                   _   (log/trace "Fetching openid configuration" {:url url})
                   res (d/chain url
                                http/get
                                guard-status-ok)
                   exp (cache-control-extract-exp res)
                   uri (->> (-> res
                                :body
                                (slurp)
                                (json/read-str :key-fn keyword)))]
        {:exp exp, :payload uri}))))

(defn- fetch-jwks-uri
  "Fetch `jwks_uri` from openid-configuration using `iss` from `token`.
  Throws an exception when token issuer does not match issuer given in
  `opts`."
  [token {:keys [iss] :as opts}]
  (let [token-iss (-> token
                      (decode-payload)
                      :iss)]
    (when (and iss (not= iss token-iss))
      (throw (ex-info (str "Issuer does not match (" token-iss ")")
                      {:expected iss, :got token-iss})))
    (d/chain token-iss
             #(fetch-openid-configuration % opts)
             :jwks_uri)))

(defn- fetch-signing-jwks
  "Fetch and parse signing jwks for `:jwks-uri` or derive from `:iss`."
  [token {:keys [jwks-uri jwks-cache-atom] :as opts}]
  {:pre [jwks-cache-atom]}
  (d/let-flow [jwks-uri (or jwks-uri (fetch-jwks-uri token opts))]
    (deferred-cached jwks-cache-atom [:jwks jwks-uri]
      (fn []
        (d/let-flow [res  (d/chain jwks-uri
                                   http/get
                                   guard-status-ok)
                     exp  (cache-control-extract-exp res)
                     jwks (->> (-> res
                                   :body
                                   (slurp)
                                   (json/read-str :key-fn keyword)
                                   :keys)
                               (filter (fn [{:keys [use]}]
                                         (or (nil? use) (= "sig" use))))
                               (map (fn [{:keys [kid alg] :as k}]
                                      [kid {:alg        alg
                                            :public-key (keys/jwk->public-key k)}]))
                               (into {}))]
          {:exp exp, :payload jwks})))))

(def ^:private
  buddy-unsign-opt-keys #{:iss :aud :sub :exp :nbf :iat :max-age :now :leeway})

(def ^:private one-day (* 60 60 24))

(defn unsign-access-token
  "Decode `token` using JWK from `:jwks-uri`.

  Throw an exception like `buddy.sign.jwt/unsign` when validation
  fails but also checks extra claims passed into `opts` and throws
  exceptions on those when not exactly matching.

  Additionally `:algs` defines the allowed signing algorithms and
  defaults to `#{:rs256}`."
  [token {:keys [algs jwks-cache-atom max-age]
          :or   {algs    #{:rs256}
                 max-age one-day}
          :as   opts}]
  {:pre [jwks-cache-atom]}
  (let [{:keys [typ kid alg]} (jwt/decode-header token)]
    (when-not (contains? algs alg)
      (throw (ex-info (str "Unsupported signing algorithm (" (name alg) ")")
                      {:type :validation :cause :alg})))

    (d/let-flow [jwks (fetch-signing-jwks token opts)
                 jwk  (-> jwks (get kid))]
      (when-not (= "at+jwt" typ)
        (throw (ex-info (str "Not an access token (" typ ")")
                        {:type :validation :cause :typ})))
      (when-not jwk
        (throw (ex-info (str "No matching jwk found (" kid ")")
                        {:type :validation :cause :kid})))

      (let [data (jwt/unsign token (:public-key jwk) (assoc opts :alg alg))]

        ;; workaround issue in jwt/unsign; missing iat will not validate max-age
        (when (and (not (:iat data)) max-age)
          (throw (ex-info (str "Can not determine maximum token age (" max-age ")")
                          {:type :validate :cause :max-age})))

        ;; handle custom claims
        (doseq [[k v] (-> opts
                          (select-keys (set/difference
                                        (set (keys opts))
                                        (into #{:algs :jwks-uri :jwks-cache-atom}
                                              buddy-unsign-opt-keys))))]
          (when-not (= v (get data k))
            (throw (ex-info (format "Claim %s does not match (%s)" (name k) (get data k))
                            {:type :validation :cause k}))))

        data))))

(comment
  (def token
    (let [res (-> (System/getenv "OAUTH_TOKEN_ENDPOINT")
                  (http/post {:headers {"content-type" "application/json"}
                              :body    (json/write-str
                                        {"client_id"     (System/getenv "OAUTH_CLIENT_ID")
	                                 "client_secret" (System/getenv "OAUTH_CLIENT_SECRET")
	                                 "audience"      (System/getenv "OAUTH_AUDIENCE")
	                                 "grant_type"    "client_credentials"})})
                  (deref)
                  (update :body slurp)
                  (update :body json/read-str :key-fn keyword))]
      (-> res :body :access_token)))

  (jwt/decode-header token)

  (let [c (atom {})]
    (unsign-access-token token {:jwks-cache-atom c
                                :aud             (System/getenv "OAUTH_AUDIENCE")})))
