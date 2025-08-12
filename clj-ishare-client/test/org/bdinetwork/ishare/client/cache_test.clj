;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.cache-test
  (:require [clojure.core.cache :as core.cache]
            [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.ishare.client.cache :as sut]))

(def token-gen
  (let [a (atom 0)]
    (fn [] (swap! a inc))))

(deftest expires-cache
  (testing "caching"
    (let [f     (fn [_]
                  {:status http-status/ok
                   :body   {"expires_in" 3600, "access_token" (token-gen)}})
          cache (atom (sut/expires-cache-factory sut/bearer-token-expires-at))
          f'    #(sut/get-through-cache-atom cache f %)
          req   :dummy
          res   (f' req)]
      (is (= res (f' req)) "cached response")
      (is (not= res (f' :other)) "other response")

      (swap! cache core.cache/evict req)
      (is (not= res (f' req)) "new respsone")))

  (testing "not caching on expires in 0 seconds"
    (let [f     (fn [_] {:status http-status/ok
                         :body   {"expires_in" 0, "access_token" (token-gen)}})
          cache (atom (sut/expires-cache-factory sut/bearer-token-expires-at))
          f'    #(sut/get-through-cache-atom cache f %)
          req   :dummy
          res   (f' req)]
      (is (not= res (f' req)) "new response")))

  (testing "not caching on unexpected response"
    (let [f     (fn [_] {:status http-status/unauthorized
                         :body   {:some-value (token-gen)}})
          cache (atom (sut/expires-cache-factory sut/bearer-token-expires-at))
          f'    #(sut/get-through-cache-atom cache f %)
          req   :dummy
          res   (f' req)]
      (is (not= res (f' req )) "new response"))))
