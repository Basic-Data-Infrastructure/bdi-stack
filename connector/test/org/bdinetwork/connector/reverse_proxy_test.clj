;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy-test
  (:require [aleph.http :as http]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [nl.jomco.http-status-codes :as http-status]
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

(defn- run-backend []
  (wait-connection
   #(run-jetty backend-handler
               {:host  backend-host
                :port  backend-port
                :join? false})
   backend-host backend-port))

(def rewrite (sut/make-target-rewrite-fn backend-url))

(defn- run-proxy []
  (wait-connection
   #(aleph.http/start-server (sut/make-handler rewrite)
                             {:host             proxy-host
                              :port             proxy-port
                              :shutdown-timeout 0})
   proxy-host proxy-port))

(use-fixtures :once
  (fn [f]
    (let [backend (run-backend)
          proxy   (run-proxy)]
      (try
        (f)
        (finally
          (.close proxy)
          (.wait-for-close proxy)
          (.stop backend))))))

(deftest base
  (let [{:keys [status headers]} @(http/get proxy-url {:throw-exceptions? false})]
    (is (= http-status/ok status)
        "request succeeded")
    (is (= "reverse-proxy" (get headers "x-test"))
        "got x-test header from backend")
    (is (= "application/edn" (get headers "content-type"))
        "got inbound request")))

(deftest multiple-cookies
  (let [{:keys [headers]} @(http/get proxy-url {:throw-exceptions? false})]
    (is (= ["foo=1" "bar=2"] (http/get-all headers "set-cookie"))
        "2 cookies set")))

(deftest x-forwarded-headers
  (let [{:keys [body]}             @(http/get proxy-url {:throw-exceptions? false})
        {inbound-headers :headers} (-> body slurp edn/read-string)]
    (is (= proxy-scheme (get inbound-headers "x-forwarded-proto"))
        "x-forwarded-proto set")
    (is (= (str proxy-port) (get inbound-headers "x-forwarded-port"))
        "x-forwarded-port set")
    (is (= (str proxy-host ":" proxy-port) (get inbound-headers "x-forwarded-host"))
        "x-forwarded-host set"))

  (testing "with upstream x-forwarded headers"
    (let [upstream-scheme            "yelp"
          upstream-port              "31415"
          upstream-host              "example.com"
          headers                    {"x-forwarded-proto" upstream-scheme
                                      "x-forwarded-port"  upstream-port
                                      "x-forwarded-host"  upstream-host}
          {:keys [body]}             @(http/get proxy-url
                                                {:headers           headers
                                                 :throw-exceptions? false})
          {inbound-headers :headers} (-> body slurp edn/read-string)]
      (is (= upstream-scheme (get inbound-headers "x-forwarded-proto"))
          "x-forwarded-proto set")
      (is (= upstream-port (get inbound-headers "x-forwarded-port"))
          "x-forwarded-port set")
      (is (= upstream-host (get inbound-headers "x-forwarded-host"))
          "x-forwarded-host set"))))
