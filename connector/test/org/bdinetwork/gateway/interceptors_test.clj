;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.interceptors-test
  (:require [clojure.test :refer [deftest is testing]]
            [org.bdinetwork.gateway.interceptors :as sut]))

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

(deftest comp-interceptors
  (let [i1 (sut/interceptor
            :name "i1"
            :enter (fn [ctx] (update ctx :visits (fnil conj []) :i1-enter))
            :leave (fn [ctx] (update ctx :visits (fnil conj []) :i1-leave)))
        i2 (sut/interceptor
            :name "i2"
            :enter (fn [ctx] (update ctx :visits (fnil conj []) :i2-enter))
            :leave (fn [ctx] (update ctx :visits (fnil conj []) :i2-leave)))
        i3 (sut/interceptor
            :name "i3"
            :enter (fn [ctx] (update ctx :visits (fnil conj []) :i3-enter))
            :leave (fn [ctx] (update ctx :visits (fnil conj []) :i3-leave)))
        i  (sut/comp-interceptors [i1 i2 i3] :name "test" :doc "test")]
    (is (= "test" (:name i) (:doc i)))
    (is (= [:i1-enter :i2-enter :i3-enter]
           (:visits ((:enter i) {}))))
    (is (= [:i3-leave :i2-leave :i1-leave]
           (:visits ((:leave i) {}))))))
