;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.logging
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as http-status]))

(defn- error-message-from-response
  [{:keys [body]}]
   (or (:message body)
       (str body)))

(defn- summary
  "Format a summary of the given request / response pair.

  Example:

    GET http://localhost:80/path?foo=bar HTTP/1.1 200 OK"
  [{:keys [request-method
           scheme
           server-name
           server-port
           uri
           query-string
           protocol]}
   {:keys [status]}]
  (format "%s %s://%s:%s%s%s %s / %d %s"
          (string/upper-case (name request-method))
          (name scheme)
          server-name
          server-port
          uri
          (if query-string (str "?" query-string) "")
          protocol
          status
          (http-status/->description status)))

(defn wrap-logging
  "Ring middleware logging request/response.

  If the response has an error status, logs as an error line. If
  response has an `:exception` key, include the exception in the
  log. Use `wrap-server-error` to convert thrown exceptions into `500
  Server Error` responses.

  If the repsonse has a non-error status, log as an info line."
  [handler]
  (fn logging-wrapper
    [request]
    (let [{:keys [status exception] :as response} (handler request)]
      (cond
        (http-status/client-error-status? status)
        (log/warnf "%s: %s" (summary request response) (error-message-from-response response))

        (http-status/server-error-status? status)
        (if exception
          (log/errorf exception "%s: %s" (summary request response) (ex-message exception))
          (log/errorf "%s: %s" (summary request response) (error-message-from-response response)))

        :else
        (log/info (summary request response)))
      response)))

(defn wrap-server-error
  "Ring middleware converting thrown exceptions into `500 Server Error` responses.

  Includes the thrown exception in the response as `:exception`."
  [f]
  (fn server-error-wrapper
    [request]
    (try
      (f request)
      (catch Exception e
        {:status    http-status/internal-server-error
         :exception e
         :body      {:error "Internal server error"}}))))
