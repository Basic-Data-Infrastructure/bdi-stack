;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.response
  (:require [nl.jomco.http-status-codes :as http-status]))

(def not-found
  {:status  http-status/not-found
   :headers {"content-type" "text/plain"}
   :body    "Not Found"})

(def bad-gateway
  {:status  http-status/bad-gateway
   :headers {"content-type" "text/plain"}
   :body    "Bad Gateway"})

(def bad-request
  {:status  http-status/bad-request
   :headers {"content-type" "text/plain"}
   :body    "Bad Request"})

(def forbidden
  {:status  http-status/forbidden
   :headers {"content-type" "text/plain"}
   :body    "Forbidden"})

(def service-unavailable
  {:status  http-status/service-unavailable
   :headers {"content-type" "text/plain"}
   :body    "Service Unavailable"})

(def unauthorized
  {:status  http-status/unauthorized
   :headers {"content-type" "text/plain"}
   :body    "Unauthorized"})
