;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.assocation-register.system
  (:require [buddy.core.keys :refer [private-key]]
            [clojure.string :as string]
            [org.bdinetwork.assocation-register.data-source :as ds]
            [org.bdinetwork.assocation-register.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))

(defn run-system
  []
  (let [handler (web/make-handler (ds/yaml-in-memory-data-source-factory "test/example-config.yml")
                                  {:server-id "EU.EORI.SERVER"
                                   :x5c (x5c "test/pem/server.x5c.pem")
                                   :private-key (private-key "test/pem/server.key.pem")})]
    (run-jetty handler {:join? true :port 8080 :hostname "localhost"})))
