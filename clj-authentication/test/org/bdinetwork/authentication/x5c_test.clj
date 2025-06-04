;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication.x5c-test
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [org.bdinetwork.authentication.x5c :as x5c]))

(deftest fingerprint
  (doseq [party ["ca" "client" "association_register" "authorization_register"]]
    (is (= (-> (shell/sh "openssl" "x509" "-in" (str "test-config/" party ".cert.pem") "-noout" "-fingerprint" "-sha256")
               :out
               (string/replace #".*Fingerprint=" "")
               (string/replace #"[:\r\n]" ""))
           (x5c/fingerprint
            (x5c/cert (first (x5c/pem->x5c (str "test-config/" party ".cert.pem")))))) ;; TODO use io/resource
        (str "fingerprint from openssl matches for " party))))
