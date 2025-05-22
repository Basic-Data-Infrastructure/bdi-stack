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

(defn- proxy-handler [{{req :request} :outgoing :as r}]
  (let [res
        (d/catch
            (-> req
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
                (assoc :pool ingress-connection-pool)

                (http/request))
            (fn proxy-handler-catch [e]
              (log/error e "failed to launch inbound request" r)
              response/service-unavailable))]
    (-> r
        (assoc-in [:incoming :response] res)
        (assoc-in [:outgoing :response] res))))

(defn wrap-forwarded-headers
  "Add `x-forwarded-proto`, `x-forwarded-host` and `x-forwarded-port` headers to backend request.

  The allows the backend to create local redirects and set domain
  cookies."
  [f]
  (fn forwarded-headers-wrapper
    [{{{{:strs [host
                x-forwarded-proto
                x-forwarded-host
                x-forwarded-port]} :headers
        :keys                      [scheme server-port]} :request}
      :incoming :as r}]
    (let [proto (or x-forwarded-proto (name scheme))
          host  (or x-forwarded-host host)
          port  (or x-forwarded-port server-port)]
      (f (cond-> r
           proto
           (assoc-in [:outgoing :request :headers "x-forwarded-proto"] proto)

           host
           (assoc-in [:outgoing :request :headers "x-forwarded-host"] host)

           port
           (assoc-in [:outgoing :request :headers "x-forwarded-port"] (str port)))))))

(defn wrap-multi-set-cookies
  "Handle multiple `set-cookie` headers.

  The `set-cookie` header is the only header which can appear multiple
  times, aleph joins them together to a single string with a `,` which
  is not the way to set multiple cookies."
  [handler]
  (fn multi-set-cookies-wrapper [r]
    (let [{{res :response} :incoming :as r} (handler r)]
      (assoc-in r [:outgoing :response]
                (d/let-flow [{:keys [headers]} res]
                  (if-let [v (try (http/get-all headers "set-cookie")
                                  ;; maybe on be a aleph.http.common/HeaderMap
                                  (catch Throwable _ nil))]
                    (assoc-in res [:headers "set-cookie"] (vec v))
                    res))))))

(def handler
  (-> proxy-handler
      (wrap-forwarded-headers)
      (wrap-multi-set-cookies)))
