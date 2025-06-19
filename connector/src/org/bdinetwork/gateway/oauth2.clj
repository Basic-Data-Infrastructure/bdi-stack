;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.oauth2
  (:require [aleph.http :as http]
            [buddy.core.keys :as keys]
            [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
            [clojure.set :as set]))

(defn- fetch-signing-jwks [url]
  ;; TODO cache these
  (->> (-> url
           (http/get)
           (deref)
           (update :body slurp)
           (update :body json/read-str :key-fn keyword)
           :body
           :keys)
       (filter (fn [{:keys [use]}]
                 (or (nil? use) (= "sig" use))))
       (map (fn [{:keys [kid alg] :as k}]
              [kid {:alg alg
                    :public-key (keys/jwk->public-key k)}]))
       (into {})))

(def ^:private
  buddy-unsign-opt-keys #{:iss :aud :sub :exp :nbf :iat :max-age :now :leeway})

(def ^:private one-day (* 60 60 24))

(defn decode-access-token
  "Decode `token` using JWK from `:jwks-uri`.

  Throw an exception like `buddy.sign.jwt/unsign` when validation
  fails but also checks extra claims passed into `opts` and throws
  exceptions on those when not exactly matching.

  Additionally `:algs` defines the allowed signing algorithms and
  defaults to `#{:rs256}`."
  [token {:keys [algs jwks-uri max-age]
          :or   {algs    #{:rs256}
                 max-age one-day}
          :as   opts}]
  (let [{:keys [typ kid alg]} (jwt/decode-header token)]
    (when-not (contains? algs alg)
      (throw (ex-info (str "Unsupported signing algorithm (" (name alg) ")")
                      {:type :validation :cause :alg})))

    (let [jwks (fetch-signing-jwks jwks-uri)
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
                                        (into #{:algs :jwks-uri}
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

  (decode-access-token token {:jwks-uri (System/getenv "OAUTH_JWKS_URI")
                              :aud      (System/getenv "OAUTH_AUDIENCE")}))
