(ns org.bdinetwork.association-register.authentication.x5c
  "Validate x5c chains"
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.hash :as hash]
            [clojure.string :as string]
            [buddy.core.certificates :as certificates])
  (:import java.io.StringReader
           org.bouncycastle.cert.X509CertificateHolder
           org.bouncycastle.asn1.x509.KeyUsage))

;; In Java, certificate chains are known as "Certification Paths"

;; https://docs.oracle.com/javase/8/docs/technotes/guides/security/certpath/CertPathProgGuide.html
;; https://docs.oracle.com/javase/8/docs/technotes/guides/security/cert3.html
;; https://docs.oracle.com/javase/8/docs/api/java/security/cert/PKIXRevocationChecker.html
;; https://docs.oracle.com/javase/6/docs/api/index.html?java/security/cert/CertPathBuilder.html

;; this generates a java.security.cert.Certificate
;; which is different from a bouncy castle CertificateHolder

;; (defn- cert
;;   [s]
;;   (with-open [is (-> (str "-----BEGIN CERTIFICATE-----\n"
;;                           s
;;                           "\n-----END CERTIFICATE-----\n")
;;                      .getBytes
;;                      ByteArrayInputStream.)]
;;     (-> (CertificateFactory/getInstance "X509" "BC")
;;         (.generateCertificate is))))

;; (defn path-validator
;;   []
;;   (CertPathValidator/getInstance "PKIX" "BC"))

;; (defn cert-path
;;   [cert-strs]
;;   (.generateCertPath (CertificateFactory/getInstance "X.509" "BC")
;;                      (map cert cert-strs)))

;; (defn validate-chain
;;   [strs]
;;   ;; this relies on PKIXParameters containing the trust roots
;;   ;; which we don't have (scheme owner only providers CA certificate finger prints!)
;;   (.validate (path-validator) (cert-path strs) (PKIXParameters. #{})))

(defn- cert-reader
  "Convert base64 encoded certificate string into a reader for parsing
  as a PEM."
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

(defn trusted-cert?
  [cert {:strs [trusted_list] :as _data-source}]
  (let [cert-subject (str (.getSubject cert))
        cert-fingerprint (fingerprint cert)]
    (some (fn [{:strs [subject certificate_fingerprint status validity]}]
            (and (= subject cert-subject)
                 (= "Valid" validity)
                 (= "Granted" status)
                 (= (string/upper-case certificate_fingerprint) cert-fingerprint)))
          trusted_list)))

(defn signing-certificate?
  [cert]
  (some-> cert
          .getExtensions
          KeyUsage/fromExtensions
          (.hasUsages KeyUsage/nonRepudiation)))

(defn validate-chain
  ;; TODO: this is a simplistic implementation, we must support revocations
  [certs data-source]
  (let [certs (map cert certs)]
    (when (signing-certificate? (first certs))
      (loop [[c1 c2 :as certs] certs]
        (if c2
          (or (and (certificates/valid-on-date? c1)
                   (certificates/verify-signature c1 c2))
              (recur (next certs)))
          (and (certificates/valid-on-date? c1)
               (certificates/verify-signature c1 c1)
               (trusted-cert? c1 data-source)))))))
