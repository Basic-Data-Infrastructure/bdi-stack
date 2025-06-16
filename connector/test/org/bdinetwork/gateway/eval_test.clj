;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
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

(deftest substitute-symbols
  (is (= :UNDEFINED
         (sut/substitute-symbols {} 'foo)))
  (is (= [1 2 3.1415 [4 5 {2.7182 :e, :pi 3.1415}]]
         (sut/substitute-symbols '{pi 3.1415, e 2.7182}
                                 '[1 2 pi [4 5 {e :e, :pi pi}]]))))
