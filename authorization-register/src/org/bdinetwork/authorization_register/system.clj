;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.system
  (:require [nl.jomco.resources :refer [mk-system Resource closeable]]
            [org.bdinetwork.authorization-register.in-memory-policies :refer [in-memory-policies]]
            [org.bdinetwork.authorization-register.web :as web]
            [org.bdinetwork.service-provider.remote-association :refer [remote-association]]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.string :as string]))

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
  (close [jetty]
    (.stop jetty)))

(defn run-system
  [{:keys [server-id x5c private-key association-server-id association-server-url] :as config}]
  {:pre [server-id x5c private-key association-server-id association-server-url]}
  (mk-system [policies    (closeable (in-memory-policies))
              association (closeable (remote-association #:ishare {:client-id          server-id
                                                                   :x5c                x5c
                                                                   :private-key        private-key
                                                                   :satellite-id       association-server-id
                                                                   :satellite-base-url association-server-url}))
              app         (closeable (web/mk-app {:policy-store policies
                                                  :policy-view  policies
                                                  :association  association}
                                                 config))
              jetty       (run-jetty app (assoc config :join? false))]))
