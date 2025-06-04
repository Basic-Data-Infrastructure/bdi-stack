;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.system
  (:require [aleph.http :as http]
            [nl.jomco.resources :refer [mk-system closeable]]
            [org.bdinetwork.gateway :as gateway]
            [org.bdinetwork.gateway.rules :as rules]))

;; force loading BDI interceptor multi methods
#_{:clj-kondo/ignore [:unused-namespace]}
(require '[org.bdinetwork.connector.interceptors :as _bdi-interceptors])

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
  (mk-system [gateway (-> rules-file
                          (rules/read-rules-file config)
                          (gateway/make-gateway))
              _server (start-server gateway config)]))
