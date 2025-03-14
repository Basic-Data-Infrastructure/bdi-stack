;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.authentication.x5c-test
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [org.bdinetwork.ring.association :as association]
            [org.bdinetwork.ring.authentication.x5c :as x5c]
            [org.bdinetwork.ring.in-memory-association :refer [in-memory-association read-source]]))

(defn pem->x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))

(deftest fingerprint
  (doseq [party ["ca" "client" "association_register" "authorization_register"]]
    (is (= (-> (shell/sh "openssl" "x509" "-in" (str "test-config/" party ".cert.pem") "-noout" "-fingerprint" "-sha256")
               :out
               (string/replace #".*Fingerprint=" "")
               (string/replace #"[:\r\n]" ""))
           (x5c/fingerprint
            (x5c/cert (first (pem->x5c (str "test-config/" party ".cert.pem"))))))
        (str "fingerprint from openssl matches for " party))))


(def client-x5c (pem->x5c "test-config/association_register.x5c.pem"))
(def trusted-list (association/trusted-list (in-memory-association (read-source "test-config/association-register-config.yml"))))

(deftest validate-chain
  (is (x5c/validate-chain client-x5c trusted-list)
      "Full chain including trusted CA"))
