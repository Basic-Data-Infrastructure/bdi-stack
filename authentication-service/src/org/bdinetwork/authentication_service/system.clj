;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication-service.system
  (:require [clojure.string :as string]
            [nl.jomco.resources :refer [mk-system Resource]]
            [org.bdinetwork.authentication-service.web :as web]
            [org.bdinetwork.ring.remote-association :refer [remote-association]]
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
  (close [jetty]
    (.stop jetty)))

(defn run-system
  [{:keys [server-id x5c private-key association-server-id association-server-url] :as config}]
  {:pre [server-id x5c private-key association-server-id association-server-url]}
  (mk-system [association (remote-association #:ishare {:client-id          server-id
                                                        :x5c                x5c
                                                        :private-key        private-key
                                                        :satellite-id       association-server-id
                                                        :satellite-base-url association-server-url})
              app         (web/mk-app {:association  association}
                                      config)
              jetty       (run-jetty app (assoc config :join? false))]
    {:jetty       jetty
     :app         app
     :association association}))
