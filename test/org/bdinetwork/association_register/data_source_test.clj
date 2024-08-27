;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.data-source-test
  (:require [clojure.test :refer [deftest is testing]]
            [org.bdinetwork.association-register.data-source :as ds]))

(def ds
  (ds/yaml-in-memory-data-source-factory "test/example-config.yml"))

(deftest party-test
  (is (ds/party ds "EU.EORI.NL000000001")
      "Data Source contains parties")
  (is (nil? (ds/party ds "EU.EORI.NL000000002"))
      "No results for unknown party id"))
