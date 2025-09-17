;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.interceptors-test
  (:require [aleph.http :as http]
            [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.gateway :as gateway]
            [org.bdinetwork.gateway.interceptors :refer [->interceptor]]
            [org.bdinetwork.test-helper :refer [jwks-keys mk-token openid-uri proxy-url start-openid start-proxy]])
  (:import (java.time Instant)))

(deftest oauth2-bearer-token
  (with-resources
      [_proxy   (start-proxy (gateway/make-gateway
                              {:rules [{:match {:uri "/"}
                                        :interceptors
                                        (mapv #(->interceptor % {})
                                              [['oauth2/bearer-token
                                                {:aud "test-audience"
                                                 :iss openid-uri}
                                                {:realm "test"}]
                                               ['respond
                                                {:status 200
                                                 :body   '(get-in ctx [:oauth2/bearer-token-claims :sub])}]])}]}))
       _openid (start-openid jwks-keys)]
    (testing "no token"
      (let [{:keys [status body]} @(http/get proxy-url {:throw-exceptions? false})]
        (is (= http-status/unauthorized status))
        (is (= "Unauthorized"
               (slurp body)))))

    (testing "bad token"
      (let [{:keys [status body]}
            @(http/get proxy-url
                       {:throw-exceptions? false
                        :headers           {"authorization" "Bearer test"}})]
        (is (= http-status/unauthorized status))
        (is (= "Message seems corrupt or manipulated"
               (slurp body)))))

    (testing "wrong audience"
      (let [token (mk-token {:iat (.getEpochSecond (Instant/now))
                             :iss openid-uri
                             :aud "bad"
                             :sub "test-subject"})
            {:keys [status body]}
            @(http/get proxy-url
                       {:throw-exceptions? false
                        :headers           {"authorization" (str "Bearer " token)}})]
        (is (= http-status/unauthorized status))
        (is (= "Audience does not match test-audience" ;; TODO helpful but a security issue
               (slurp body)))))

    (testing "success"
      (let [token (mk-token {:iat (.getEpochSecond (Instant/now))
                             :iss openid-uri
                             :aud "test-audience"
                             :sub "test-subject"})
            {:keys [status body]}
            @(http/get proxy-url
                       {:throw-exceptions? false
                        :headers           {"authorization" (str "Bearer " token)}})]
        (is (= http-status/ok status))
        (is (= "test-subject"
               (slurp body)))))))
