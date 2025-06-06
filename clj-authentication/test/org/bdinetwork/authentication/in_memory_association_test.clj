;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication.in-memory-association-test
  (:require [clojure.test :refer [deftest is]]
            [org.bdinetwork.authentication.association :as association]
            [org.bdinetwork.authentication.x5c :as x5c]
            [org.bdinetwork.in-memory-association :refer [in-memory-association read-source]]))

(def ds
  (in-memory-association (read-source "test-config/association-register-config.yml")))

(deftest party-test
  (is (association/party ds "EU.EORI.CLIENT")
      "Data Source contains parties")
  (is (nil? (association/party ds "EU.EORI.NO_SUCH_PARTY"))
      "No results for unknown party id"))

(def client-x5c (x5c/pem->x5c "test-config/association_register.x5c.pem"))
(def trusted-list (association/trusted-list (in-memory-association (read-source "test-config/association-register-config.yml"))))

(deftest validate-chain
  (is (x5c/validate-chain client-x5c trusted-list)
      "Full chain including trusted CA"))
