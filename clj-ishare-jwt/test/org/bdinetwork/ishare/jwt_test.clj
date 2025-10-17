;;; SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.jwt-test
  (:require [buddy.core.keys :as keys]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [org.bdinetwork.ishare.jwt :as jwt]))

;; corresponding to generated certificates and keys in test/pem
(def client-id "EU.EORI.CLIENT")
(def server-id "EU.EORI.AA")

(defn x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           io/resource
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))

(deftest client-assertion-is-valid-ishare-jwt
  (is (jwt/unsign-token
       (jwt/make-client-assertion #:ishare {:client-id client-id
                                            :server-id server-id
                                            :private-key (keys/private-key (io/resource "test-config/client.key.pem"))
                                            :x5c (x5c "test-config/client.x5c.pem")})))

  (is (jwt/unsign-client-assertion
       (jwt/make-client-assertion #:ishare {:client-id client-id
                                            :server-id server-id
                                            :private-key (keys/private-key (io/resource "test-config/client.key.pem"))
                                            :x5c (x5c "test-config/client.x5c.pem")}))))
