;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.system
  (:require [aleph.http :as http]
            [clojure.string :as string]
            [nl.jomco.resources :refer [Resource mk-system closeable]]
            [org.bdinetwork.connector.gateway :as gateway]))

(defn stop-server
  [s]
  (.close s)
  (.wait-for-close s))

(defn start-server
  [handler config]
  (closeable (http/start-server handler config)
             stop-server))

(defn run-system
  [{:keys [rules-file] :as config}]
  (mk-system [gateway (gateway/make-gateway (gateway/read-rules rules-file))
              server (start-server gateway config)]))
