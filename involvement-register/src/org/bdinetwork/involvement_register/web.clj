;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.involvement-register.web
  (:require [compojure.core :refer [defroutes GET POST DELETE]]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.ring.logging :refer [wrap-logging wrap-server-error]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]))

(defroutes routes
  (GET "/involvements/:id" {:keys [client-id]}
    {:status 200}
    )
  (constantly
   (not-found "Resource not found")))

(defn make-handler
  []
  (-> routes
      (wrap-server-error)
      (wrap-json-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-logging)))
