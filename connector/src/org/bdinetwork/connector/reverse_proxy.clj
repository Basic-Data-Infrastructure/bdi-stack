;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.reverse-proxy
  (:require [aleph.http :as http]
            [manifold.deferred :as d]
            [nl.jomco.http-status-codes :as http-status])
  (:import (java.net URL)))

(def ingress-connection-pool
  (http/connection-pool {:connection-options {:keep-alive? true}}))

(defn handler [rewrite-fn req]
  (d/catch
      (http/request (-> req

                        ;; keep all relevant for request
                        (select-keys [:protocol
                                      :request-method :uri :headers
                                      :query-string :body])

                        ;; no magic
                        (assoc :throw-exceptions? false
                               :follow-redirects? false)

                        ;; a specialized connection pool
                        (assoc :pool ingress-connection-pool)

                        ;; rewrite request
                        (rewrite-fn)))
      (constantly
        {:status  http-status/service-unavailable
         :headers {"content-type" "text/plain"}
         :body    "Service Unavailable"})))

(defn wrap-forwarded-headers
  "Add `x-forwarded-proto`, `x-forwarded-host` and `x-forwarded-port` headers to backend request.

  The allows the backend to create local redirects and set domain
  cookies."
  [f]
  (fn forwarded-headers-wrapper
    [{{:strs [host
              x-forwarded-proto
              x-forwarded-host
              x-forwarded-port]} :headers
      :keys                      [scheme server-port]
      :as                        req}]
    (let [proto       (or x-forwarded-proto (name scheme))
          host        (or x-forwarded-host host)
          port        (or x-forwarded-port server-port)]
      (f (cond-> req
           proto
           (assoc-in [:headers "x-forwarded-proto"] proto)

           host
           (assoc-in [:headers "x-forwarded-host"] host)

           port
           (assoc-in [:headers "x-forwarded-port"] (str port)))))))

(defn wrap-multi-set-cookies
  "Handle multiple `set-cookie` headers.

  The `set-cookie` header is the only header which can appear multiple
  times, aleph joins them together to a single string with a `,` which
  is not the way to set multiple cookies."
  [handler]
  (fn multi-set-cookies-wrapper [req]
    (d/let-flow [{:keys [headers] :as res} (handler req)]
      (if-let [v (try (http/get-all headers "set-cookie")
                      ;; maybe on be a aleph.http.common/HeaderMap
                      (catch Throwable _ nil))]
        (assoc-in res [:headers "set-cookie"] (vec v))
        res))))

(defn make-handler [rewrite-fn]
  (-> (partial handler rewrite-fn)
      (wrap-forwarded-headers)
      (wrap-multi-set-cookies)))

(defn make-target-rewrite-fn [target-url]
  (let [url    (URL. target-url)
        target {:scheme      (keyword (.getProtocol url))
                :server-name (.getHost url)
                :server-port (.getPort url)}]
    (fn target-rewrite-fn [req]
      (merge req (select-keys target [:scheme :server-name :server-port])))))

(defn -main [target-url & [port]]
  (http/start-server (make-handler (make-target-rewrite-fn target-url))
                     {:port (if port (Integer/parseInt port) 8081)}))
