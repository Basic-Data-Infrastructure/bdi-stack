(ns org.bdinetwork.authorization-register.system-test
  (:require [org.bdinetwork.authorization-register.system :as system]
            [org.bdinetwork.association-register.system :as association]
            [org.bdinetwork.service-provider.in-memory-association :refer [in-memory-association read-source]]
            [org.bdinetwork.authorization-register.policies :as policies]
            [org.bdinetwork.ishare.client :as client]
            [buddy.core.keys :as keys]
            [nl.jomco.resources :refer [with-resources]]
            [clojure.test :refer [deftest is]]))

(def association-config
  {:server-id                "EU.EORI.ASSOCIATION-REGISTER"
   :private-key              (keys/private-key "test-config/association_register.key.pem")
   :public-key               (keys/public-key "test-config/association_register.cert.pem")
   :x5c                      (system/x5c "test-config/association_register.x5c.pem")
   :association              (in-memory-association (read-source "test-config/association-register-config.yml"))
   :port                     9991
   :access-token-ttl-seconds 300})

(def auth-register-config
  {:server-id                "EU.EORI.AUTHORIZATION-REGISTER"
   :private-key              (keys/private-key "test-config/authorization_register.key.pem")
   :public-key               (keys/public-key "test-config/authorization_register.cert.pem")
   :x5c                      (system/x5c "test-config/authorization_register.x5c.pem")
   :port                     9992
   :association-server-id    (:server-id association-config)
   :association-server-url   (str "http://localhost:" (:port association-config))
   :access-token-ttl-seconds 300})

(def client-config
  {:ishare/client-id     "EU.EORI.CLIENT"
   :ishare/private-key   (keys/private-key "test-config/client.key.pem")
   :ishare/x5c           (system/x5c "test-config/client.x5c.pem")
   :ishare/satellite-id  (:server-id association-config)
   :ishare/satellite-url (str "http://localhost:" (:port association-config))})

(def delegation-mask
  {:delegationRequest
   {:policyIssuer (:ishare/client-id client-config)
    :target       {:accessSubject "EU.EORI.NLPRECIOUSG"}
    :policySets   [{:policies [{:rules  [{:effect "Permit"}]
                                :target {:resource    {:type        "klantordernummer"
                                                       :identifiers ["112233"]
                                                       :attributes  ["*"]}
                                         :actions     ["BDI.PICKUP"]
                                         :environment {:licenses ["0001"]
                                                       :serviceProviders []}}}]}]}})

(deftest system-test
  (with-resources [association-system (association/run-system association-config)
                   authorization-system (system/run-system auth-register-config)]
    (let [resp (client/exec (assoc client-config
                                   :ishare/endpoint "http://localhost:9991"
                                   :ishare/server-id (:server-id association-config)
                                   :ishare/message-type :access-token))]
      (is (= 200 (:status resp)))
      (is (string? (get-in resp [:body "access_token"]))))

    (let [resp (client/exec (assoc client-config
                                   :ishare/endpoint "http://localhost:9992"
                                   :ishare/server-id (:server-id auth-register-config)
                                   :ishare/message-type :access-token))]
      (is (= 200 (:status resp)))
      (is (string? (get-in resp [:body "access_token"]))))


    #_    (is (= {}
           (client/exec (assoc client-config
                               :ishare/endpoint "http://localhost:9992"
                               :ishare/server-id (:server-id auth-register-config)
                               :ishare/message-type :delegation
                               :ishare/params delegation-mask))))))
