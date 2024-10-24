;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.system
  (:require [buddy.core.keys :refer [private-key]]
            [clojure.string :as string]
            [nl.jomco.resources :refer [Resource mk-system closeable]]
            [org.bdinetwork.association-register.web :as web]
            [org.bdinetwork.service-provider.in-memory-association :refer [in-memory-association]]
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

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(defn run-system
  [config]
  (mk-system [association (closeable (in-memory-association (:data-source config)))
              handler (closeable (web/make-handler association config))
              jetty (run-jetty handler {:join?    false
                                        :port     (:port config)
                                        :hostname (:hostname config)})]
    {:association association
     :handler     handler
     :jetty       jetty}))
