;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors
  (:require [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [org.bdinetwork.connector.eval :refer [evaluate]]
            [org.bdinetwork.connector.reverse-proxy :as reverse-proxy]))

(defn interceptor [& {:keys [name enter leave]}]
  {:pre [name (or enter leave)]}
  (cond-> {:name name, :enter identity, :leave identity}
    enter
    (assoc :enter #(do (log/debug (str "ENTER " name)) (enter %)))

    leave
    (assoc :leave #(do (log/debug (str "LEAVE " name)) (leave %)))))



(def eval-env
  {'assoc       assoc
   'assoc-in    assoc-in
   'get         get
   'merge       merge
   'select-keys select-keys
   'str         str
   'update      update})

(def interceptors
  {'respond
   (fn [id response]
     (interceptor
      :name (str id " " (pr-str response))
      :doc  "Respond with given value."
      :enter
      (fn [ctx] (assoc ctx :response response))))

   'request/update
   (fn [id & form]
     (interceptor
      :name (str id " " (pr-str form))
      :doc  "Update the incoming request using eval on the request object."
      :enter
      (fn  [{:keys [request vars] :as ctx}]
        (let [form (list* (first form) 'request (drop 1 form))]
          (assoc ctx :request
                 (evaluate form (-> eval-env
                                    (merge vars)
                                    (assoc 'request request))))))))

   'response/update
   (fn [id & form]
     (interceptor
      :name (str id " " (pr-str form))
      :doc  "Update the outgoing request using eval on the response object."
      :args form
      :leave
      (fn response-eval-leave [{:keys [request response vars] :as ctx}]
        (let [form (list* (first form) 'response (drop 1 form))]
          (assoc ctx :response
                 (d/let-flow [response response]
                   (evaluate form (-> eval-env
                                      (merge vars)
                                      (assoc 'request request
                                             'response response)))))))))

   'reverse-proxy/forwarded-headers
   (fn [id]
     (interceptor
      :name (str id)
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
                   (assoc-in [:proxy-request-overrides :headers "x-forwarded-proto"] proto)

                   host
                   (assoc-in [:proxy-request-overrides :headers "x-forwarded-host"] host)

                   port
                   (assoc-in [:proxy-request-overrides :headers "x-forwarded-port"] (str port)))))))

   'reverse-proxy/proxy-request
   (fn [id]
     (interceptor
      :name (str id)
      :doc  "Execute proxy request and populate response."
      :enter
      (fn proxy-request-enter
        [{:keys [request proxy-request-overrides] :as ctx}]
        (assoc ctx :response
               (reverse-proxy/proxy-request (merge-with merge request proxy-request-overrides))))))})



(defn ->interceptor [[interceptor-id & args :as interceptor]]
  (if-let [make-interceptor (get interceptors interceptor-id)]
    (apply make-interceptor interceptor-id args)
    (throw (ex-info "unknown interceptor for interceptor" {:interceptor interceptor}))))
