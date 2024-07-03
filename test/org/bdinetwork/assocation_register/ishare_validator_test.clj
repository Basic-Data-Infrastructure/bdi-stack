(ns org.bdinetwork.assocation-register.ishare-validator-test
  (:require [org.bdinetwork.assocation-register.ishare-validator :as sut]
            [clojure.test :refer [deftest is]]))

(deftest smoke-test
  (get-in sut/ishare-spec-data ["components" "schemas" "Party"])
  (is (seq (sut/validate {} "Party"))))
