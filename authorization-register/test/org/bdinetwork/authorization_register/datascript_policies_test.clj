;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.datascript-policies-test
  (:require [clojure.test :refer [deftest testing]]
            [org.bdinetwork.authorization-register.datascript-policies :refer [in-memory-policies]]
            [org.bdinetwork.authorization-register.policies-test :refer [test-policy-store-impl]]))

(deftest in-memory-policies-test
  (testing "in-memory policy store"
    (test-policy-store-impl in-memory-policies)))
