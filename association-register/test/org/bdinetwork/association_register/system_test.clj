(ns org.bdinetwork.association-register.system-test
  (:require [org.bdinetwork.association-register.system :as system]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.ishare.client :as client]
            [buddy.core.keys :as keys]
            [org.bdinetwork.service-provider.in-memory-association :refer [read-source]]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]))

;; TODO: make port numbers configurable for tests / automatically use free port

(def client-config
  {:ishare/satellite-base-url "http://localhost:8080"
   :ishare/satellite-id       "EU.EORI.SERVER"
   :ishare/client-id          "EU.EORI.CLIENT"
   :ishare/private-key        (client/private-key "test/pem/client.key.pem")
   :ishare/x5c                (system/x5c "test/pem/client.x5c.pem")})

(def system-config
  {:private-key              (client/private-key "test/pem/server.key.pem")
   :public-key               (keys/public-key "test/pem/server.cert.pem")
   :x5c                      (system/x5c "test/pem/server.x5c.pem")
   :data-source              (read-source "test/test-config.yml")
   :server-id                "EU.EORI.SERVER"
   :hostname                 "localhost"
   :port                     8080
   :access-token-ttl-seconds 600})

(deftest test-system
  (with-resources [s (system/run-system system-config)]
    (let [{:keys [status] :as response} (client/exec (-> client-config
                                                         (client/satellite-request)
                                                         (assoc :ishare/message-type :party
                                                                :ishare/party-id "EU.EORI.CLIENT")))]
      (is (= http-status/ok status))
      (is (= (get-in response [:ishare/result :party_info :party_id]) "EU.EORI.CLIENT")))

    (let [{:keys [status] :as response} (client/exec (-> client-config
                                                         (client/satellite-request)
                                                         (assoc :ishare/message-type :party
                                                                :ishare/party-id "EU.EORI.NONE")))]
      (is (= http-status/ok status))
      ;; what should be the format when no party exists?
      (is (nil? (get-in response [:ishare/result :party_info :party_id]))))))
