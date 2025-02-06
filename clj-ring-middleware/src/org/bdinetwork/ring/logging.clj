;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.logging
  (:require [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :refer [client-error-status? server-error-status?] :as status]))

(defn- error-message-from-response
  [{:keys [body]}]
   (or (:message body)
       (str body)))

(defn wrap-logging
  "Ring middleware logging request/reponse.

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
        (client-error-status? status)
        (log/warn (str (:status response) " " (:request-method request) " " (:uri request) (error-message-from-response response)))

        (server-error-status? status)
        (if exception
          (log/error exception (str (:status response) " " (:request-method request) " " (:uri request) " " (ex-message exception)))
          (log/error (str (:status response) " " (:request-method request) " " (:uri request) " " (error-message-from-response response))))

        :else
        (log/info (str (:status response) " " (:request-method request) " " (:uri request))))
      response)))

(defn wrap-server-error
  "Ring middleware converting thrown exceptions into `500 Server Error` responses.

  Includes the thrown exception in the response as `:exception`."
  [f]
  (fn server-error-wrapper
    [request]
    (try (f request)
         (catch Exception e
           {:status    status/internal-server-error
            :exception e
            :body      {:error "Internal server error"}}))))
