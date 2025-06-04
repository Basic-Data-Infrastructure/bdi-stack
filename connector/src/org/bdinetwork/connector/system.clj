;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.system
  (:require [aleph.http :as http]
            [nl.jomco.resources :refer [mk-system closeable]]
            [org.bdinetwork.gateway :as gateway]
            [org.bdinetwork.gateway.rules :as rules]))

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
  (mk-system [gateway (gateway/make-gateway (rules/read-rules-file rules-file))
              _server (start-server gateway config)]))
