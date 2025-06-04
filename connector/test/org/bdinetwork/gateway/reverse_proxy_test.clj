;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.reverse-proxy-test
  (:require [aleph.http :as http]
            [clojure.test :refer [deftest is use-fixtures]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.gateway.reverse-proxy :as sut]
            [org.bdinetwork.test-helper :refer [backend-url proxy-url start-backend start-proxy]]))

(defn- backend-handler [req]
  {:status  200
   :headers {"content-type" "application/edn"
             "set-cookie"   ["foo=1" "bar=2"]
             "x-test"       "reverse-proxy"}
   :body    (-> req
                (update :body slurp)
                (pr-str))})

(defn- proxy-handler [req]
  (sut/proxy-request (assoc req :url backend-url)))

(use-fixtures :once
  (fn [f]
    (with-resources [_backend (start-backend backend-handler)
                     _proxy   (start-proxy proxy-handler)]
      (f))))

(deftest proxy-request
  (let [{:keys [status headers]} @(http/get proxy-url {:throw-exceptions? false})]
    (is (= http-status/ok status)
        "request succeeded")
    (is (= "reverse-proxy" (get headers "x-test"))
        "got x-test header from backend")
    (is (= "application/edn" (get headers "content-type"))
        "got inbound request")
    (is (= ["foo=1" "bar=2"] (http/get-all headers "set-cookie"))
        "2 cookies set")))
