(ns org.bdinetwork.assocation-register.data-source-test
  (:require [org.bdinetwork.assocation-register.data-source :as ds]
            [clojure.test :refer [deftest is are testing]]))

(def ds
  (ds/read-data-source "test/example-config.yml"))

;; peek inside
(def parties (ds "parties"))

(deftest parties-test
  (testing "Smoke tests"
    (is (seq ds)
        "Data Source is not empty")
    (is (seq parties)
        "Data Source contains parties")
    (is (= (count parties) (count (ds/parties ds {})))
        "No params returns all parties"))
  (testing "active_only"
    (is (= (count parties) (count (ds/parties ds {"active_only" true}))))
    (is (= 0 (count (ds/parties ds {"active_only" false})))))

  (testing "role"
    (is (= (count parties) (count (ds/parties ds {"role" "ServiceConsumer"}))))
    (is (= 0 (count (ds/parties ds {"role" "IdentityBroker"}))))))
