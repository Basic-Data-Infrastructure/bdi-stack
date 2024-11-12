;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.ishare-validator-test
  (:require [org.bdinetwork.ring.ishare-validator :as sut]
            [clojure.test :refer [deftest is]]))

(deftest smoke-test
  (is (get-in sut/ishare-spec-data ["components" "schemas" "Party"]))
  (is (seq (sut/validate {} ["components" "schemas" "Party"]))))
