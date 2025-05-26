;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy-test
  (:require [aleph.http :as http]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.connector.reverse-proxy :as sut]
            [org.bdinetwork.connector.reverse-proxy-test-helper :refer [backend-url proxy-host proxy-port proxy-scheme proxy-url start-backend start-proxy]]))

(use-fixtures :once
  (fn [f]
    (with-resources [_backend (start-backend)
                     _proxy   (start-proxy (sut/make-target-rewrite-fn backend-url))]
      (f))))

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
