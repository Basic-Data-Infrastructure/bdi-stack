;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.cache-test
  (:require [clojure.core.memoize :as memoize]
            [clojure.test :refer [deftest is testing]]
            [org.bdinetwork.ishare.client.cache :as sut])
  (:import (java.time Instant)))

(def token-gen
  (let [a (atom 0)]
    (fn [] (swap! a inc))))

(deftest expires-cache
  (testing "caching"
    (let [f     (fn [_]
                  {::sut/expires-at (.plusSeconds (Instant/now) 3600)
                   :access-token    (token-gen)})
          f'    (memoize/memoizer f (sut/expires-cache-factory))
          req   :dummy
          res   (f' req)]
      (is (= res (f' req)) "cached response")
      (is (not= res (f' :other)) "other response")
      (is (= res (f' req)) "cached response")))

  (testing "not caching on expires in 0 seconds"
    (let [f     (fn [_]
                  {::sut/expires-at (Instant/now)
                   :access-token    (token-gen)})
          f'    (memoize/memoizer f (sut/expires-cache-factory))
          req   :dummy
          res   (f' req)]
      (is (not= res (f' req)) "new response"))))
