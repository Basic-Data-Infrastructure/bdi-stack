;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authentication-service.web
  (:require [compojure.core :refer [defroutes]]
            [org.bdinetwork.ring.association :refer [wrap-association]]
            [org.bdinetwork.ring.authentication :as authentication]
            [org.bdinetwork.ring.diagnostic-context :refer [wrap-request-context]]
            [org.bdinetwork.ring.logging :refer [wrap-logging wrap-server-error]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]))

(defroutes routes
  (constantly (not-found "Resource not found.")))

(defn mk-app
  [{:keys [association]} config]
  {:pre [association]}
  (-> routes
      (authentication/wrap-authentication config)
      (wrap-association association)
      (wrap-server-error)
      (wrap-json-response)
      (wrap-json-params {:key-fn identity})
      (wrap-params)
      (wrap-logging)
      (wrap-request-context)))
