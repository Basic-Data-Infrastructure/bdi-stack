;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.test-helper
  (:require [nl.jomco.resources :refer [Resource]]
            [org.bdinetwork.connector.system :as system]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.net InetSocketAddress Socket)))

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
