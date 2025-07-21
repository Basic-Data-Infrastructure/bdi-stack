;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway
  (:require [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [org.bdinetwork.gateway.interceptors :as interceptors]
            [org.bdinetwork.gateway.matcher :as matcher]
            [org.bdinetwork.gateway.response :as response]
            [ring.middleware.params :as ring-params])
  (:import (java.nio.charset Charset)
           (java.util UUID)))

(defn match-rule [rules req]
  (some (fn [{:keys [match] :as rule}]
          (when-let [vars (matcher/match match req)]
            (update rule :vars merge vars)))
        rules))

(defn- exec [interceptor phase ctx]
  (let [info {:phase phase, :interceptor (:name interceptor)}]
    (try
      (log/debug "Executing" info)
      (interceptors/exec phase interceptor ctx)
      ;; TODO: interceptor can return deferred!
      (catch Exception e
        (log/error e "Exception during execution of interceptor" info)
        (assoc ctx
               :error (assoc info :exception e)

               ;; It's up some error handling interceptor to create a
               ;; new response.
               :response response/bad-gateway)))))

(def ^:private ^Charset utf-8 (Charset/forName "UTF-8"))

(defn make-gateway [{:keys [vars rules]}]
  (fn gateway [req]
    (let [req (ring-params/assoc-query-params req utf-8)]
      (try
        (if-let [{:keys [interceptors] :as rule} (match-rule rules req)]
          (d/loop [ctx {:trace-id (UUID/randomUUID)
                        :request  req
                        :vars     (merge vars (:vars rule))}
                   enter-stack interceptors
                   leave-stack []]
            (d/let-flow [{:keys [response error] :as ctx} ctx]
              (if-let [interceptor (if (or response error)
                                     (first leave-stack)
                                     (first enter-stack))]
                (cond
                  ;; down the list `enter` handlers
                  (not (or response error))
                  (d/recur (exec interceptor :enter ctx)
                           (next enter-stack)
                           (into [interceptor] leave-stack))

                  ;; back up the list `leave` handlers
                  (not error)
                  (d/recur (exec interceptor :leave ctx)
                           nil
                           (next leave-stack))

                  ;; TODO: Why is there no `error` stack?
                  ;; back up the list `error` handlers
                  error
                  (d/recur (exec interceptor :error ctx)
                           nil
                           (next leave-stack)))

                (or response
                    (do
                      (log/error "Interceptors depleted without a response")
                      response/bad-gateway)))))
          response/not-found)
        (catch Exception e
          (log/error e "Failed to process request" req)
          response/bad-gateway)))))
