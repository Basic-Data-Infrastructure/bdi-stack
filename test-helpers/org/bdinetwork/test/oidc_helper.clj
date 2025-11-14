;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.test.oidc-helper
  "Implements a minimal OIDC server for testing."
  (:require [buddy.core.keys :as keys]
            [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [Resource]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.time Instant)))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(def kid "test-kid")

(defn mk-token
  [claims  private-key]
  (jwt/sign (cond-> claims
              (not (contains? claims :exp))
              (assoc :exp (+ 120 (.getEpochSecond (Instant/now))))
              (not (contains? claims :iat))
              (assoc :iat (.getEpochSecond (Instant/now))))
            private-key
            {:header {:typ "at+jwt" :kid kid}
             :alg    :rs256}))

(defn mk-openid-handler
  [{:keys [jwk jwks-uri private-key]}]
  {:pre [jwk jwks-uri private-key]}
  (fn [{:keys [uri] :as req}]
    (case uri
      "/.well-known/openid-configuration"
      {:status  http-status/ok
       :headers {"content-type" "application/json"}
       :body    (json/write-str {:jwks_uri jwks-uri})}

      "/.well-known/jwks.json"
      {:status  http-status/ok
       :headers {"content-type" "application/json"}
       :body    (json/write-str {:keys [jwk]})}

      "/token"
      (let [audience (-> req :body slurp json/read-str (get "audience"))
            token    (mk-token {:aud audience} private-key)]
        {:status  http-status/ok
         :headers {"content-type" "application/json"}
         :body    (json/write-str {:access_token token
                                   :token_type   "Bearer"
                                   :expires_in   300})}))))

(defn openid-test-server
  [{:keys [host port private-key public-key]}]
  (let [jwks-uri (str "http://" host ":" port "/.well-known/jwks.json")]
    (-> {:jwk         (assoc (keys/public-key->jwk public-key)
                             :kid kid)
         :jwks-uri    jwks-uri
         :private-key private-key}
        mk-openid-handler
        (run-jetty {:join? false
                    :host  host
                    :port  port}))))
