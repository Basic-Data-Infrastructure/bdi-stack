;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.service-provider.in-memory-association-test
  (:require [clojure.test :refer [deftest is]]
            [org.bdinetwork.service-provider.association :as association]
            [org.bdinetwork.service-provider.in-memory-association :refer [in-memory-association read-source]]))

(def ds
  (in-memory-association (read-source "test-config/association-register-config.yml")))

(deftest party-test
  (is (association/party ds "EU.EORI.CLIENT")
      "Data Source contains parties")
  (is (nil? (association/party ds "EU.EORI.NO_SUCH_PARTY"))
      "No results for unknown party id"))
