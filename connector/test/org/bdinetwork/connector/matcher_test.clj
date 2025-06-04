;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.matcher-test
  (:require [clojure.test :refer [deftest is testing]]
            [org.bdinetwork.connector.matcher :as sut]))

(deftest match
  (testing "exact matches"
    (is (sut/match nil nil))
    (is (sut/match 1 1))
    (is (sut/match {:foo {:bar "bar"}} {:foo {:bar "bar"}})))

  (testing "extra values"
    (is (sut/match {:foo {:bar "bar"}} {:foo {:bar "bar", :extra 1}, :extra 2})
        "extra values"))

  (testing "mismatches"
    (is (not (sut/match {:foo {:bar "bar"}} {:foo {:bar "bar!"}})))
    (is (not (sut/match {:foo {:bar "bar"}} {:foo {:par "bar"}})))
    (is (not (sut/match {:foo {:bar "bar"} :zoo "yelp"} {:foo {:bar "bar"}})))
    (is (not (sut/match {:foo {:bar "bar"} :zoo 'yelp} {:foo {:bar "bar"}}))
        "no placeholders for non existing keys"))

  (testing "vars"
    (is (= {'foo "bar"}
           (sut/match [{:foo 'foo} 1 #"huh.*"]
                      [{:foo "bar"} 1 "huh!"])))

    (is (= {'foo 1}
           (sut/match {:foo 'foo, :bar 'foo}
                      {:foo 1, :bar 1})))
    (is (= false
           (sut/match {:foo 'foo, :bar 'foo}
                      {:foo 1, :bar 2})))))
