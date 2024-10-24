(ns org.bdinetwork.service-provider.in-memory-association-test
  (:require [clojure.test :refer [deftest is]]
            [org.bdinetwork.service-provider.association :as association]
            [org.bdinetwork.service-provider.in-memory-association :refer [in-memory-association read-source]]))

(def ds
  (in-memory-association (read-source "test/example-config.yml")))

(deftest party-test
  (is (association/party ds "EU.EORI.NL000000001")
      "Data Source contains parties")
  (is (nil? (association/party ds "EU.EORI.NL000000002"))
      "No results for unknown party id"))
