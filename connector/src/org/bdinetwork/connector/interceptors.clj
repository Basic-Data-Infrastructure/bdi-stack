;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors
  (:require [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [org.bdinetwork.connector.eval :refer [evaluate]]
            [org.bdinetwork.connector.reverse-proxy :as reverse-proxy]))

(defn interceptor [& {:keys [name args doc enter leave]}]
  {:pre [name (or enter leave)]}
  (let [log-action (if args
                     #(log/debug (str % " " name) args)
                     #(log/debug (str % " " name)))]
    (cond-> {:name name}
      args
      (assoc :args args)

      doc
      (assoc :doc doc)

      enter
      (assoc :enter #(do (log-action "ENTER") (enter %)))

      leave
      (assoc :leave #(do (log-action "LEAVE") (leave %))))))

(defmulti rule->interceptor (fn [x] (if (vector? x) [(first x) '..] x)))

(defmethod rule->interceptor :default
  [spec]
  (throw (ex-info "unknown interceptor" {:spec spec})))



(defmethod rule->interceptor '[respond ..]
  [[_ response]]
  (interceptor
    :name "respond"
    :doc  "Respond with given value."
    :enter
    (fn [ctx] (assoc ctx :response response))))

(defmethod rule->interceptor '[request/eval ..]
  [[_ & form]]
  (interceptor
    :name "request/eval"
    :doc  "Update the incoming request using eval on the request object."
    :args form
    :enter
    (fn  [{:keys [request eval-env] :as ctx}]
      (let [form (list* (first form) 'request (drop 1 form))]
        (assoc ctx :request
               (evaluate form (assoc eval-env 'request request)))))))



(defmethod rule->interceptor '[response/eval ..]
  [[_ & form]]
  (interceptor
    :name "response/eval"
    :doc  "Update the outgoing request using eval on the response object."
    :args form
    :leave
    (fn response-eval-leave [{:keys [request response eval-env] :as ctx}]
      (let [form (list* (first form) 'response (drop 1 form))]
        (assoc ctx :response
               (d/let-flow [response response]
                 (evaluate form (assoc eval-env
                                       'request request
                                       'response response))))))))



(defmethod rule->interceptor 'reverse-proxy/forwarded-headers
  [_]
  (interceptor
    :name "reverse-proxy/forwarded-headers"
    :doc  "Add `x-forwarded-proto`, `x-forwarded-host` and `x-forwarded-port` headers to request for backend.

  The allows the backend to create local redirects and set domain
  cookies."
    :enter (fn forwarded-headers-enter
               [{{{:strs [host
                          x-forwarded-proto
                          x-forwarded-host
                          x-forwarded-port]} :headers
                  :keys                      [scheme server-port]} :request
                 :as                                               ctx}]
               (let [proto (or x-forwarded-proto (name scheme))
                     host  (or x-forwarded-host host)
                     port  (or x-forwarded-port server-port)]
                 (cond-> ctx
                   proto
                   (assoc-in [:proxy-request :headers "x-forwarded-proto"] proto)

                   host
                   (assoc-in [:proxy-request :headers "x-forwarded-host"] host)

                   port
                   (assoc-in [:proxy-request :headers "x-forwarded-port"] (str port)))))))

(defmethod rule->interceptor 'reverse-proxy/proxy-request
  [_]
  (interceptor
    :name "reverse-proxy/proxy-request"
    :doc  "Execute proxy request and populate response."
    :enter
    (fn proxy-request-enter
      [{:keys [request proxy-request-overrides] :as ctx}]
      (assoc ctx :response
             (reverse-proxy/proxy-request (merge-with merge request proxy-request-overrides))))))
