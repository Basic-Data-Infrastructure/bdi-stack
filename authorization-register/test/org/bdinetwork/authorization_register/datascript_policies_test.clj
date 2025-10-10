(ns org.bdinetwork.authorization-register.datascript-policies-test
  (:require [clojure.test :refer [deftest testing]]
            [org.bdinetwork.authorization-register.datascript-policies :refer [in-memory-policies]]
            [org.bdinetwork.authorization-register.policies-test :refer [test-policy-store-impl]]))

(deftest in-memory-policies-test
  (testing "in-memory policy store"
    (test-policy-store-impl in-memory-policies)))
