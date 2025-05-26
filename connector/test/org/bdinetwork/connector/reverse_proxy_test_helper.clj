;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy-test-helper
  (:require [aleph.http :as http]
            [nl.jomco.resources :refer [Resource closeable mk-system]]
            [org.bdinetwork.connector.reverse-proxy :as sut]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.net InetSocketAddress Socket)))

(def backend-scheme "http")
(def backend-host "127.0.0.1")
(def backend-port 11001)
(def backend-url (str backend-scheme "://" backend-host ":" backend-port))

(def proxy-scheme "http")
(def proxy-host "127.0.0.2")
(def proxy-port 11000)
(def proxy-url (str proxy-scheme "://" proxy-host ":" proxy-port))

(def wait-timeout 5000)

(defn- ensure-connection
  [{:keys [host port]}]
  (doto (Socket.)
    (.connect (InetSocketAddress. host port) wait-timeout)
    (.close)))

(defn- backend-handler [req]
  {:status  200
   :headers {"content-type" "application/edn"
             "set-cookie"   ["foo=1" "bar=2"]
             "x-test"       "reverse-proxy"}
   :body    (-> req
                (update :body slurp)
                (pr-str))})

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(defn start-backend []
  (run-jetty backend-handler
             {:host  backend-host
              :port  backend-port
              :join? false}))

(defn close-aleph-server
  "Blocking close of aleph HTTP server.

  Blocks until server is shut down"
  [server]
  (.close server)
  (.wait-for-close server))

(defn start-proxy [rewrite]
  ;; using mk-system here because we want to wait for
  ;; aleph.http/start-server to be running before returning
  (mk-system [server (closeable (aleph.http/start-server (sut/make-handler rewrite)
                                                         {:host             proxy-host
                                                          :port             proxy-port
                                                          :shutdown-timeout 0})
                                close-aleph-server)]
    (ensure-connection {:host proxy-host
                        :port proxy-port})
    {:server server}))
