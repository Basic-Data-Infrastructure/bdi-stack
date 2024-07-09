(ns org.bdinetwork.assocation-register.data-source-test
  (:require [org.bdinetwork.assocation-register.data-source :as ds]
            [clojure.test :refer [deftest is are testing]]))

(def ds
  (ds/read-data-source "test/example-config.yml"))

;; peek inside
(def parties (ds "parties"))

(def query-examples
  ;; count results, params pairs
  [1 {"active_only" true}
   0 {"active_only" false}
   1 {"adherenceEnddate" "2025-02-13T00:00:00.000Z"}
   0 {"adherenceEnddate" "2025-02-13T01:00:00.000Z"}
   1 {"adherenceStartdate" "2024-02-12T00:00:00.000Z"}
   0 {"adherenceStartdate" "2024-02-12T10:00:00.000Z"}
   1 {"adherenceStatus" "Active"}
   0 {"adherenceStatus" "Revoked"}
   1 {"authorizationRegistryID" "EU.EORI.NL000000004"}
   0 {"authorizationRegistryID" "EU.EORI.NL000000005"}
   1 {"authorizationRegistryName" "iSHARE Test Authorization Registry"}
   0 {"authorizationRegistryName" "Some Authorization Registry"}
   1 {"certified_only" false}
   0 {"certified_only" true}
   1 {"role" "ServiceConsumer"}
   0 {"role" "IdentityBroker"}
   1 {"compliancyVerified" false
      "role"               "ServiceConsumer"}
   0 {"compliancyVerified" true
      "role"               "ServiceConsumer"}
   1 {"legalAdherence" false
      "role"           "ServiceConsumer"}
   0 {"legalAdherence" true
      "role"           "ServiceConsumer"}
   1 {"loA" "Low"
      "role"           "ServiceConsumer"}
   0 {"loA" "High"
      "role"           "ServiceConsumer"}
   1 {"name" "*"}
   0 {"name" "Foo*"}
   1 {"name" "AB*Trucking"}
   1 {"name" "ABC Trucking"}
   0 {"name" "XYZ Trucking"}
   1 {"countriesOfOperation" "Netherlands, Kingdom of the"}
   0 {"countriesOfOperation" "Belgium"}
   ])

(deftest parties-test
  (testing "Smoke tests"
    (is (seq ds)
        "Data Source is not empty")
    (is (seq parties)
        "Data Source contains parties")
    (is (= (count parties) (count (ds/parties ds {})))
        "No params returns all parties"))

  (doseq [[c params] (partition 2 query-examples)]
    (testing (str "Parameters " params)
      (is (= c (count (ds/parties ds params)))
          (str  c " result for " params)))))
