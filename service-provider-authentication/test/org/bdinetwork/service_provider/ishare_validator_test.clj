(ns org.bdinetwork.service-provider.ishare-validator-test
  (:require [org.bdinetwork.service-provider.ishare-validator :as sut]
            [clojure.test :refer [deftest is]]))

(deftest smoke-test
  (is (get-in sut/ishare-spec-data ["components" "schemas" "Party"]))
  (is (seq (sut/validate {} ["components" "schemas" "Party"]))))
