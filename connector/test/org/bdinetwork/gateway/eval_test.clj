;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.eval-test
  (:require [clojure.test :refer [deftest is]]
            [org.bdinetwork.gateway.eval :as sut]))

(deftest evaluate
  (is (= {:x [31415]}
         (sut/evaluate '{:x [(+ 1 (- x 1))]}
                       {'+ +
                        '- -
                        'x 31415}))))
