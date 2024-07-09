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

   1 {"certificate_subject_name" "CN=ABC Trucking,SERIALNUMBER=EU.EORI.NL000000001,OU=Test,O=iSHARETest,C=NL"}
   0 {"certificate_subject_name" "CN=ABC Trucking,SERIALNUMBER=EU.EORI.NL000000001,OU=Test,O=iSHARETest,C=BE"}
   1 {"certified_only" false}
   0 {"certified_only" true}
   1 {"companyEmail" "test@example.com"}
   0 {"companyEmail" "foo@example.com"}
   1 {"companyPhone" "+311234567890"}
   0 {"companyPhone" "+312345678901"}
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
   1 {"dataSpaceID" "SOME-DATASPACE"}
   0 {"dataSpaceID" "OTHER-DATASPACE"}
   1 {"dataSpaceTitle" "Some Dataspace"}
   0 {"dataSpaceTitle" "Other Dataspace"}
   ;; TODO: date_time

   1 {"eori" "EU.EORI.NL000000001"}
   0 {"eori" "EU.EORI.NL000000002"}

   1 {"framework" "iSHARE"}
   0 {"framework" "BDI"}

   1 {"name" "*"}
   0 {"name" "Foo*"}
   1 {"name" "AB*Trucking"}
   1 {"name" "ABC Trucking"}
   0 {"name" "XYZ Trucking"}
   1 {"countriesOfOperation" "Netherlands, Kingdom of the"}
   0 {"countriesOfOperation" "Belgium"}

   0 {"date_time" "2024-01-14T00:00:00.000Z"}
   1 {"date_time" "2024-02-12T00:00:00.000Z"}
   1 {"date_time" "2024-12-12T00:00:00.000Z"}
   1 {"date_time" "2025-02-13T00:00:00.000Z"}
   0 {"date_time" "2025-03-14T00:00:00.000Z"}

   1 {"publiclyPublishable" false}
   0 {"publiclyPublishable" true}

   1 {"registarSatelliteID" "EU.EORI.NL000000000"}
   0 {"registarSatelliteID" "EU.EORI.NL000000001"}

   1 {"sectorIndustry" "Metals & Mining"}
   0 {"sectorIndustry" "Paper & Forest Products"}

   1 {"subjectName" "CN=ABC Trucking,SERIALNUMBER=EU.EORI.NL000000001,OU=Test,O=iSHARETest,C=NL"}
   0 {"subjectName" "CN=ABC Trucking,SERIALNUMBER=EU.EORI.NL000000001,OU=Test,O=iSHARETest,C=BE"}

   1 {"tags" "some"}
   0 {"tags" "none"}

   1 {"webSiteUrl" "https://example.com/"}
   0 {"webSiteUrl" "https://sub.example.com/"}])

(deftest parties-test
  (testing "Smoke tests"
    (is (seq ds)
        "Data Source is not empty")
    (is (seq parties)
        "Data Source contains parties")
    (is (= (count parties) (count (get-in (ds/parties ds {}) ["parties_info" "data"])))
        "No params returns all parties"))

  (doseq [[c params] (partition 2 query-examples)]
    (testing (str "Parameters " params)
      (is (= c (get-in (ds/parties ds params) ["parties_info" "total_count"]))
          (str  c " result for " params)))))
