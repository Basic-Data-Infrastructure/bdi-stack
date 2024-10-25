(ns org.bdinetwork.service-provider.in-memory-association-test
  (:require [clojure.test :refer [deftest is]]
            [org.bdinetwork.service-provider.association :as association]
            [org.bdinetwork.service-provider.in-memory-association :refer [in-memory-association read-source]]))

(def ds
  (in-memory-association (read-source "test-config/association-register-config.yml")))

(deftest party-test
  (is (association/party ds "EU.EORI.CLIENT")
      "Data Source contains parties")
  (is (nil? (association/party ds "EU.EORI.NO_SUCH_PARTY"))
      "No results for unknown party id"))
