(ns org.bdinetwork.assocation-register.authentication.x5c-test
  (:require [org.bdinetwork.assocation-register.authentication.x5c :as x5c]
            [org.bdinetwork.assocation-register.data-source :as data-source]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]))

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
  (doseq [party ["ca" "client" "server"]]
    (is (= (string/trim (slurp (str "test/pem/" party ".fingerprint")))
           (x5c/fingerprint
            (x5c/cert (first (pem->x5c (str "test/pem/" party ".cert.pem"))))))
        (str "fingerprint from openssl matches for " party))))

(def data-source
  (data-source/read-data-source "test/test-config.yml"))

(def client-x5c (pem->x5c "test/pem/client.x5c.pem"))

(deftest validate-chain
  (is (x5c/validate-chain client-x5c data-source)))
