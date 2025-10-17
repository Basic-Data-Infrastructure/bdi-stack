;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication.in-memory-association-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [org.bdinetwork.authentication.association :as association]
            [org.bdinetwork.authentication.in-memory-association :refer [in-memory-association read-source]]
            [org.bdinetwork.authentication.x5c :as x5c]))

(def ds (-> "test-config/association-register-config.yml"
            (io/resource)
            (read-source)
            (in-memory-association)))

(deftest party-test
  (is (association/party ds "EU.EORI.CLIENT")
      "Data Source contains parties")
  (is (nil? (association/party ds "EU.EORI.NO_SUCH_PARTY"))
      "No results for unknown party id"))

(def client-x5c (-> "test-config/association_register.x5c.pem"
                    (io/resource)
                    (x5c/pem->x5c)))

(def trusted-list (-> "test-config/association-register-config.yml"
                      (io/resource)
                      (read-source)
                      (in-memory-association)
                      (association/trusted-list)))

(deftest validate-chain
  (is (x5c/validate-chain client-x5c trusted-list)
      "Full chain including trusted CA"))
