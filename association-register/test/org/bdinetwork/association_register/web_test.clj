;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.web-test
  (:require [buddy.core.keys :as keys]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.association-register.system :as system]
            [org.bdinetwork.association-register.web :as web]
            [org.bdinetwork.authentication.in-memory-association :refer [in-memory-association read-source]]))

(def system-config
  {:private-key              (keys/private-key (io/resource "test-config/association_register.key.pem"))
   :public-key               (keys/public-key (io/resource "test-config/association_register.cert.pem"))
   :x5c                      (system/x5c (io/resource "test-config/association_register.x5c.pem"))
   :association              (in-memory-association (read-source (io/resource "test-config/association-register-config.yml")))
   :server-id                "EU.EORI.SERVER"
   :hostname                 "localhost"
   :port                     11000
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
