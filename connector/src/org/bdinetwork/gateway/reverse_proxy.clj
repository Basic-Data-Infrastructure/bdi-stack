;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.reverse-proxy
  (:require [aleph.http :as http]
            [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [org.bdinetwork.gateway.response :as response]))

(def connection-pool
  (http/connection-pool {:connection-options {:keep-alive? true}}))

(defn- fix-cookies
  "Handle multiple `set-cookie` headers.

  The `set-cookie` header is the only header which can appear multiple
  times, aleph joins them together to a single string with a `,` which
  is not the way to set multiple cookies."
  [response]
  (d/let-flow [{:keys [headers]} response]
    (if-let [v (try (http/get-all headers "set-cookie")
                    ;; maybe on be a aleph.http.common/HeaderMap
                    (catch Throwable _ nil))]
      (assoc-in response [:headers "set-cookie"] (vec v))
      response)))

(defn proxy-request
  [request]
  (d/catch
      (-> request

          ;; keep all relevant for request
          (select-keys [:protocol
                        :request-method

                        :url

                        :scheme
                        :server-name
                        :server-port
                        :uri
                        :query-string

                        :headers
                        :body])

          ;; no magic
          (assoc :throw-exceptions? false
                 :follow-redirects? false)

          ;; a specialized connection pool
          (assoc :pool connection-pool)

          (http/request)
          (fix-cookies))

      (fn proxy-handler-catch [e]
        (log/error e "failed to launch inbound request" request)
        response/service-unavailable)))
