(ns org.bdinetwork.association-register.web-test
  (:require [org.bdinetwork.association-register.web :as web]
            [org.bdinetwork.association-register.system :as system]
            [buddy.core.keys :as keys]
            [org.bdinetwork.service-provider.in-memory-association :refer [in-memory-association read-source]]
            [clojure.test :refer [deftest is]]
            [clojure.string :as string]
            [nl.jomco.http-status-codes :as http-status]))

(def system-config
  {:private-key              (keys/private-key "test/pem/server.key.pem")
   :public-key               (keys/public-key "test/pem/server.cert.pem")
   :x5c                      (system/x5c "test/pem/server.x5c.pem")
   :association              (in-memory-association (read-source "test/test-config.yml"))
   :server-id                "EU.EORI.SERVER"
   :hostname                 "localhost"
   :port                     8080
   :access-token-ttl-seconds 600})

(def handler
  (web/make-handler (:association system-config) system-config))

(deftest test-web
  (let [resp (handler {:client-id      "EU.EORI.CLIENT"
                       :request-method :get
                       :uri            "/parties/EU.EORI.CLIENT"})]
    (is (= http-status/ok (:status resp)))

    (is (string/starts-with? (:body resp) "{\"party_token\":\"")))
  (let [resp (handler {:request-method :get
                       :uri            "/parties/EU.EORI.CLIENT"})]
    (is (= http-status/unauthorized (:status resp)))

    (is (string/blank? (:body resp)))))
