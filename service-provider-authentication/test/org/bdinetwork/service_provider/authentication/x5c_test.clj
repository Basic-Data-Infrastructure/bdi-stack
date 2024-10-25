(ns org.bdinetwork.service-provider.authentication.x5c-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [org.bdinetwork.service-provider.authentication.x5c :as x5c]
            [org.bdinetwork.service-provider.in-memory-association :refer [in-memory-association read-source]]
            [clojure.java.shell :as shell]))

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


(def association
  (in-memory-association (read-source "test-config/association-register-config.yml")))

(def client-x5c (pem->x5c "test-config/association_register.x5c.pem"))

(deftest validate-chain
  (is (x5c/validate-chain client-x5c association)))
