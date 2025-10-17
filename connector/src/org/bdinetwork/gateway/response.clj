;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.response
  (:require [nl.jomco.http-status-codes :as http-status]))

(defn response
  [status body]
  {:status status
   :headers {"content-type" "text/plain"}
   :body body})

(def not-found
  (response http-status/not-found
            "Not Found"))

(def bad-gateway
  (response http-status/bad-gateway
            "Bad Gateway"))

(def bad-request
  (response http-status/bad-request
            "Bad Request"))

(def forbidden
  (response http-status/forbidden
            "Forbidden"))

(def service-unavailable
  (response http-status/service-unavailable
            "Service Unavailable"))

(def unauthorized
  (response http-status/unauthorized
            "Unauthorized"))

(def ok
  (response http-status/ok
            "Ok"))
