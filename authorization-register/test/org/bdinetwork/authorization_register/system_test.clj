;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.system-test
  (:require [org.bdinetwork.authorization-register.system :as system]
            [org.bdinetwork.association-register.system :as association]
            [org.bdinetwork.ring.in-memory-association :refer [read-source]]
            [org.bdinetwork.ishare.client :as client]
            [nl.jomco.http-status-codes :as http-status]
            [buddy.core.keys :as keys]
            [nl.jomco.resources :refer [with-resources]]
            [clojure.test :refer [deftest is testing]])
  (:import java.nio.file.Files
           java.nio.file.attribute.FileAttribute))

(defn own-ar-request
  "If request has no `ishare/base-url` and `ishare/server-id`,
  set `base-url` and `server-id` from `ishare/authorization-registry-id`
  and `ishare/authorization-registry-base-url`."
  [{:ishare/keys [authorization-registry-id
                  authorization-registry-base-url
                  base-url
                  server-id]
    :as          request}]
  (if (and base-url server-id)
    request
    (assoc request
           :ishare/base-url  authorization-registry-base-url
           :ishare/server-id authorization-registry-id)))

(defmethod client/ishare->http-request ::policy ;; non-standard request
  [{delegation-evidence :ishare/params :as request}]
  {:pre [delegation-evidence]}
  (-> request
      (own-ar-request)
      (assoc :method       :post
             :path         "policy"
             :as           :json
             :json-params  delegation-evidence
             :ishare/unsign-token "policy_token"
             :ishare/lens         [:body "policy_token"])))

(def association-config
  {:server-id                "EU.EORI.ASSOCIATION-REGISTER"
   :private-key              (keys/private-key "test-config/association_register.key.pem")
   :public-key               (keys/public-key "test-config/association_register.cert.pem")
   :x5c                      (system/x5c "test-config/association_register.x5c.pem")
   :data-source              (read-source "test-config/association-register-config.yml")
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
  {:ishare/client-id          "EU.EORI.CLIENT"
   :ishare/private-key        (keys/private-key "test-config/client.key.pem")
   :ishare/x5c                (system/x5c "test-config/client.x5c.pem")
   :ishare/satellite-id       (:server-id association-config)
   :ishare/satellite-base-url (str "http://localhost:" (:port association-config))
   :throw                     false ;; we test on status codes, so don't throw in the tests
   })

(def delegation-mask
  {:delegationRequest
   {:policyIssuer (:ishare/client-id client-config)
    :target       {:accessSubject "EU.EORI.NLPRECIOUSG"}
    :policySets   [{:policies [{:rules  [{:effect "Permit"}]
                                :target {:resource    {:type        "klantordernummer"
                                                       :identifiers ["112233"]
                                                       :attributes  ["*"]}
                                         :actions     ["BDI.PICKUP"]
                                         :environment {:licenses         ["0001"]
                                                       :serviceProviders []}}}]}]}})

(def delegation-evidence
  {"policyIssuer" (:ishare/client-id client-config)
   "target"       {"accessSubject" "EU.EORI.NLPRECIOUSG"}
   "policySets"   [{"policies" [{"rules"  [{"effect" "Permit"}]
                                 "target" {"resource"    {"type"        "klantordernummer"
                                                          "identifiers" ["112233"]
                                                          "attributes"  ["*"]}
                                           "actions"     ["BDI.PICKUP"]
                                           "environment" {"licenses"         ["0001"]
                                                          "serviceProviders" []}}}]}]})

(defn temp-dir
  []
  (str (Files/createTempDirectory "authregisterdb" (make-array FileAttribute 0))))

(deftest system-test
  (let [dir (temp-dir)]
    #_{:clj-kondo/ignore [:unused-binding]}
    (with-resources [association-system (association/run-system association-config)
                     authorization-system (system/run-system (assoc auth-register-config
                                                                    :policies-db dir))]
      (let [resp (client/exec (client/access-token-request (assoc client-config
                                                                  :ishare/base-url "http://localhost:9991"
                                                                  :ishare/server-id (:server-id association-config))))]
        (is (= http-status/ok (:status resp)))
        (is (string? (get-in resp [:body "access_token"]))))

      (let [resp  (client/exec (client/access-token-request (assoc client-config
                                                                   :ishare/base-url "http://localhost:9992"
                                                                   :ishare/server-id (:server-id auth-register-config))))
            token (get-in resp [:body "access_token"])]
        (is (= http-status/ok (:status resp)))
        (is (string? token))
        (let [resp (client/exec (client/delegation-evidence-request (assoc client-config
                                                                           :ishare/bearer-token token
                                                                           :ishare/base-url "http://localhost:9992"
                                                                           :ishare/server-id (:server-id auth-register-config))
                                                                    delegation-mask))]
          (is (= http-status/ok (:status resp)))
          (is (= "Deny" (get-in resp [:ishare/result :delegationEvidence :policySets 0 :policies 0 :rules 0 :effect]))
              "Deny when no matching policy found"))

        (let [resp (client/exec (assoc client-config
                                       :ishare/bearer-token token
                                       :ishare/base-url "http://localhost:9992"
                                       :ishare/server-id (:server-id auth-register-config)
                                       :ishare/message-type ::policy
                                       :ishare/params {"delegationEvidence" delegation-evidence}))]
          (is (= http-status/ok (:status resp))
              "Policy accepted"))

        (testing "attempt to insert policy for other party"
          (let [resp (client/exec (assoc client-config
                                         :ishare/bearer-token token
                                         :ishare/base-url "http://localhost:9992"
                                         :ishare/server-id (:server-id auth-register-config)
                                         :ishare/message-type ::policy
                                         :ishare/params {"delegationEvidence" (assoc delegation-evidence "policyIssuer" "someone-else")}))]
            (is (= http-status/forbidden (:status resp))
                "Policy rejected")))

        (let [resp (client/exec (client/delegation-evidence-request
                                 (assoc client-config
                                        :ishare/bearer-token token
                                        :ishare/base-url "http://localhost:9992"
                                        :ishare/server-id (:server-id auth-register-config))
                                 delegation-mask))]
          (is (= http-status/ok (:status resp)))
          (is (= "Permit" (get-in resp [:ishare/result :delegationEvidence :policySets 0 :policies 0 :rules 0 :effect]))
              "Permit when matching policy found"))))))
