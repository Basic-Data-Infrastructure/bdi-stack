;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy-test-helper
  (:require [aleph.http :as http]
            [org.bdinetwork.connector.reverse-proxy :as sut]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.net Socket)))

(def backend-scheme "http")
(def backend-host "127.0.0.1")
(def backend-port 11001)
(def backend-url (str backend-scheme "://" backend-host ":" backend-port))

(def proxy-scheme "http")
(def proxy-host "127.0.0.2")
(def proxy-port 11000)
(def proxy-url (str proxy-scheme "://" proxy-host ":" proxy-port))

(def wait-timeout 5000)
(def wait-interval 10)

(defn- connected? [host port]
  (try
    (.close (Socket. host port))
    true
    (catch Exception _
      false)))

(defn- wait-connection [f host port]
  (let [r (f)]
    (loop [n (/ wait-timeout wait-interval)]
      (cond
        (connected? host port)
        r

        (pos? n)
        (do
          (Thread/sleep wait-interval)
          (recur (dec n)))

        :else
        (throw (ex-info "waiting timeout" {:host host, :port port}))))))

(defn- backend-handler [req]
  {:status  200
   :headers {"content-type" "application/edn"
             "set-cookie"   ["foo=1" "bar=2"]
             "x-test"       "reverse-proxy"}
   :body    (-> req
                (update :body slurp)
                (pr-str))})

(defn start-backend []
  (wait-connection
   #(run-jetty backend-handler
               {:host  backend-host
                :port  backend-port
                :join? false})
   backend-host backend-port))

(defn stop-backend [backend]
  (.stop backend))

(defn stop-proxy [proxy]
  (.close proxy)
  (.wait-for-close proxy))

(defn start-proxy [rewrite-fn]
  (wait-connection
   #(aleph.http/start-server (sut/make-handler rewrite-fn)
                             {:host             proxy-host
                              :port             proxy-port
                              :shutdown-timeout 0})
   proxy-host proxy-port))
