;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors-test
  (:require [clojure.test :refer [deftest is testing]]
            [org.bdinetwork.connector.interceptors :as sut]))

(deftest ->interceptor
  (testing "request/rewrite"
    (is (sut/->interceptor ['request/rewrite "https://example.com"]))
    (is (try
          (sut/->interceptor ['request/rewrite "https://example.com/foo"])
          false
          (catch Exception _ true))
        "should not accept path on URL")
    (is (try
          (sut/->interceptor ['request/rewrite "https://example.com/?foo"])
          false
          (catch Exception _ true))
        "should not accept query string on URL")))
