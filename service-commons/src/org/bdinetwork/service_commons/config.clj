;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.service-commons.config
  (:require [buddy.core.keys :as keys]
            [clojure.string :as string]
            [nl.jomco.envopts :as envopts]))

(defn split-x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))

(defmethod envopts/parse :private-key
  [s _]
  [(keys/private-key s)])

(defmethod envopts/parse :public-key
  [s _]
  [(keys/public-key s)])

(defmethod envopts/parse :x5c
  [s _]
  [(split-x5c s)])

(def server-party-opt-specs
  {:private-key              ["Server private key pem file" :private-key]
   :public-key               ["Server public key pem file" :public-key]
   :x5c                      ["Server certificate chain pem file" :x5c]
   :server-id                ["Server ID (EORI)" :str]
   :access-token-ttl-seconds ["Access Token TTL in seconds" :int :default 600]})

(defn config
  [env opt-specs]
  (let [[config errs] (envopts/opts env opt-specs)]
    (when errs
      (throw (ex-info  (str "Error in environment configuration\n"
                            (envopts/errs-description errs) "\n"
                            "Available environment vars:\n"
                            (envopts/specs-description opt-specs) "\n")
                       {:errs errs
                        :config config})))
    config))
