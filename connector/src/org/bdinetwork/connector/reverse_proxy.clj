;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy
  (:require [aleph.http :as http]
            [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [org.bdinetwork.connector.response :as response]))

(def ingress-connection-pool
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

(defn pk [v] (prn v) v)

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

          (pk)

          ;; a specialized connection pool
          (assoc :pool ingress-connection-pool)

          (http/request)
          (fix-cookies))

      (fn proxy-handler-catch [e]
        (log/error e "failed to launch inbound request" request)
        response/service-unavailable)))

(def proxy-request-interceptor
  ^{:doc "Execute proxy request and populate response."}
  {:enter
   (fn proxy-request-enter [{:keys [request] :as ctx}]
     (log/debug "proxy-request-enter")
     (assoc ctx :response
            (proxy-request (-> request
                               ;; get x-forwarded headers if available
                               (update :headers merge (-> ctx :proxy-request :headers))))))})

(def forwarded-headers-interceptor
  ^{:doc "Add `x-forwarded-proto`, `x-forwarded-host` and `x-forwarded-port` headers to request for backend.

  The allows the backend to create local redirects and set domain
  cookies."}
  {:enter
   (fn forwarded-headers-enter
     [{{{:strs [host
                x-forwarded-proto
                x-forwarded-host
                x-forwarded-port]} :headers
        :keys                      [scheme server-port]} :request
       :as ctx}]
     (log/debug "forwarded-headers-enter")
     (let [proto (or x-forwarded-proto (name scheme))
           host  (or x-forwarded-host host)
           port  (or x-forwarded-port server-port)]
       (cond-> ctx
         proto
         (assoc-in [:proxy-request :headers "x-forwarded-proto"] proto)

         host
         (assoc-in [:proxy-request :headers "x-forwarded-host"] host)

         port
         (assoc-in [:proxy-request :headers "x-forwarded-port"] (str port)))))})
