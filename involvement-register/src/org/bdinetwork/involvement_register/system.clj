;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.involvement-register.system
  (:require [clojure.string :as string]
            [nl.jomco.resources :refer [Resource mk-system]]
            [org.bdinetwork.involvement-register.web :as web]
            [org.bdinetwork.involvement-register.db :as db]
            [ring.adapter.jetty :refer [run-jetty]]))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(defn run-system
  [config]
  (mk-system [handler (web/make-handler)
              jetty (run-jetty handler {:join?    false
                                        :port     (:port config)
                                        :hostname (:hostname config)})]
    {:handler handler
     :jetty   jetty
     :db      (db/datasource config)}))
