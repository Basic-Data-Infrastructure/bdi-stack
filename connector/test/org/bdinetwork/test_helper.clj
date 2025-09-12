;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.test-helper
  (:require [buddy.core.keys :as keys]
            [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [Resource]]
            [org.bdinetwork.connector.system :as system]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.time Instant)
           (java.net InetSocketAddress Socket)))

(def backend-scheme :http)
(def backend-host "127.0.0.1")
(def backend-port 11001)
(def backend-url (str (name backend-scheme) "://" backend-host ":" backend-port))

(def proxy-scheme :http)
(def proxy-host "127.0.0.2")
(def proxy-port 11000)
(def proxy-url (str (name proxy-scheme) "://" proxy-host ":" proxy-port))

(def wait-timeout 5000)

(defn- ensure-connection
  [{:keys [host port]}]
  (doto (Socket.)
    (.connect (InetSocketAddress. host port) wait-timeout)
    (.close)))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(defn start-backend [handler]
  (run-jetty handler
             {:host  backend-host
              :port  backend-port
              :join? false}))

(defn start-proxy [handler]
  (let [proxy (system/start-server handler
                                   {:host             proxy-host
                                    :port             proxy-port
                                    :shutdown-timeout 0})]
    (ensure-connection {:host proxy-host
                        :port proxy-port})
    proxy))

(def ^:private oauth-private-key (-> "test-config/association_register.key.pem"
                                     (io/resource)
                                     (keys/private-key)))

(def ^:private oauth-public-key (-> "test-config/association_register.x5c.pem"
                                    (io/resource)
                                    (keys/public-key)))

(def ^:private kid "test-kid")

(defn mk-token [claims & {:keys [typ alg kid private-key]
                          :or   {typ "at+jwt"
                                 alg :rs256
                                 kid kid
                                 private-key oauth-private-key}}]
  (jwt/sign (cond-> claims
              (not (contains? claims :exp))
              (assoc :exp (+ 120 (.getEpochSecond (Instant/now)))))
            private-key
            {:header {:typ typ, :kid kid}
             :alg    alg}))

(def openid-scheme :http)
(def openid-host "127.0.0.1")
(def openid-port 11002)
(def openid-uri (str (name openid-scheme) "://" openid-host ":" openid-port))
(def jwks-uri (str openid-uri "/.well-known/jwks.json"))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server] (.stop server)))

(defn start-openid [keys]
  (run-jetty (fn [{:keys [uri]}]
               (case uri
                 "/.well-known/openid-configuration"
                 {:status  http-status/ok
                  :headers {"content-type" "application/json"}
                  :body    (json/write-str {:jwks_uri jwks-uri})}

                 "/.well-known/jwks.json"
                 {:status  http-status/ok
                  :headers {"content-type" "application/json"}
                  :body    (json/write-str {:keys keys})}))
             {:host  openid-host
              :port  openid-port
              :join? false}))

(def jwks-keys [(-> oauth-public-key
                    (keys/public-key->jwk)
                    (assoc :kid kid))])
