;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy-test-helper
  (:require [aleph.http :as http]
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
(def wait-interval 10)

(def connect-timeout (/ wait-interval 2))

(defn- connected? [host port]
  (try
    (doto (Socket.)
      (.connect (InetSocketAddress. host port) connect-timeout)
      (.close))
    true
    (catch Exception _
      false)))

(defn- wait-connection [{:keys [start-fn stop-fn host port]}]
  (let [r (start-fn)]
    (try
      (loop [n (/ wait-timeout wait-interval)]
        (cond
          (connected? host port)
          r

          (pos? n)
          (do
            (Thread/sleep wait-interval)
            (recur (dec n)))

          :else
          (throw (Exception. (str "wait timeout for " host ":" port)))))
      (catch Exception e
        (try (stop-fn r) (catch Exception _ nil))
        (throw e)))))

(defn- backend-handler [req]
  {:status  200
   :headers {"content-type" "application/edn"
             "set-cookie"   ["foo=1" "bar=2"]
             "x-test"       "reverse-proxy"}
   :body    (-> req
                (update :body slurp)
                (pr-str))})

(defn stop-backend [backend]
  (.stop backend))

(defn start-backend []
  (wait-connection {:start-fn
                    #(run-jetty backend-handler
                                {:host  backend-host
                                 :port  backend-port
                                 :join? false})
                    :stop-fn stop-backend
                    :host    backend-host
                    :port    backend-port}))

(defn stop-proxy [proxy]
  (.close proxy)
  (.wait-for-close proxy))

(defn start-proxy [rewrite]
  (wait-connection {:start-fn
                    #(aleph.http/start-server (sut/make-handler rewrite)
                                              {:host             proxy-host
                                               :port             proxy-port
                                               :shutdown-timeout 0})
                    :stop-fn stop-proxy
                    :host    proxy-host
                    :port    proxy-port}))
