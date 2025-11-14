;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.example.backend
  "An example API server without authentication / authorization.

  The backend API returns DSCA events at /v3/events endpoint.

  The BDI Connector can be configured to authenticate and authorize
  incoming requests."
  (:require [clojure.java.io :as io]
            [compojure.core :refer [GET routes]]
            [compojure.route :as route]
            [nl.jomco.http-status-codes :as status]
            [nl.jomco.resources :refer [mk-system Resource]]
            [ring.adapter.jetty :refer [run-jetty]]))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(defn content
  []
  (slurp (io/resource "org/bdinetwork/example/backend/response.json")))

(defn mk-handler
  []
  (routes
   (GET "/v3/events" [_]
     {:status status/ok
      :headers {"content-type" "application/json"}
      :body (content)})
   (route/not-found "Not found")))

(defn start
  [{:keys [port hostname] :or {hostname "localhost"}}]
  {:pre [port hostname]}
  (mk-system [handler (mk-handler)
              _jetty (run-jetty handler {:join? false :port port :hostname hostname})]))
