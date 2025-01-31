;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.authentication.x5c
  "Validate x5c chains."
  (:require [buddy.core.certificates :as certificates]
            [buddy.core.codecs :as codecs]
            [buddy.core.hash :as hash]
            [clojure.string :as string])
  (:import (java.io StringReader)
           (org.bouncycastle.asn1.x509 KeyUsage)
           (org.bouncycastle.cert X509CertificateHolder)
           (org.bouncycastle.cert.jcajce JcaX509CertificateConverter)))

(defn- cert-reader
  "Convert base64 encoded certificate string into a reader for parsing as a PEM."
  [cert-str]
  (StringReader. (str "-----BEGIN CERTIFICATE-----\n"
                      cert-str
                      "\n-----END CERTIFICATE-----\n")))

(defn cert
  [c]
  (cond
    (instance? X509CertificateHolder c)
    c

    (string? c)
    (certificates/certificate (cert-reader c))

    :else
    (certificates/certificate c)))

(defn fingerprint
  [cert]
  (-> cert
      .getEncoded
      hash/sha256
      codecs/bytes->hex
      string/upper-case))

(defn subject-name
  [cert]
  (-> (JcaX509CertificateConverter.)
      (.getCertificate cert)
      .getSubjectX500Principal
      (.getName "RFC2253" {"2.5.4.5" "SERIALNUMBER"})))

(defn trusted-cert?
  [cert trusted-list]
  (let [cert-subject (str (.getSubject cert))
        cert-fingerprint (fingerprint cert)]
    (some (fn [{:strs [subject certificate_fingerprint status validity]}]
            (and (= subject cert-subject)
                 (= "Valid" validity)
                 (= "Granted" status)
                 (= (string/upper-case certificate_fingerprint) cert-fingerprint)))
          trusted-list)))

(defn signing-certificate?
  [cert]
  (some-> cert
          .getExtensions
          KeyUsage/fromExtensions
          (.hasUsages KeyUsage/nonRepudiation)))

(defn cert-signing-certificate?
  [cert]
  (some-> cert
          .getExtensions
          KeyUsage/fromExtensions
          (.hasUsages KeyUsage/keyCertSign)))

(defn validate-chain
  ;; TODO: this is a simplistic implementation, we must support revocations
  [certs trusted-list]
  (let [certs (map cert certs)
        ca-certs (next certs)]
    (when (every? cert-signing-certificate? ca-certs)
      (when (signing-certificate? (first certs))
        (loop [[c1 c2 :as certs] certs]
          (if c2
            ;; c2 is an intermediate or root cert. c1 should be signed
            ;; by c2
            (and (certificates/valid-on-date? c1)
                 (certificates/verify-signature c1 c2)
                 (recur (next certs)))
            ;; c1 is a trusted root certificate
            (and (certificates/valid-on-date? c1)
                 (trusted-cert? c1 trusted-list))))))))
