;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.gateway
  (:require [clojure.tools.logging :as log]
            [org.bdinetwork.connector.matcher :as matcher]
            [org.bdinetwork.connector.response :as response]))

(defn match-rule [rules req]
  (some (fn [{:keys [match] :as rule}]
          (when-let [vars (matcher/match match req)]
            (update rule :vars merge vars)))
        rules))

(defn make-gateway [{:keys [vars rules]}]
  (fn gateway [req]
    (try
      (if-let [{:keys [interceptors] :as rule} (match-rule rules req)]
        (loop [{:keys [response] :as ctx} {:request req
                                           :vars    (merge vars (:vars rule))}
               enter-stack                interceptors
               leave-stack                []]
          (if response
            (if-let [{:keys [leave]} (first leave-stack)]
              (recur (leave ctx)
                     nil
                     (next leave-stack))
              response)
            (if-let [{:keys [enter] :as interceptor} (first enter-stack)]
              (recur (enter ctx)
                     (next enter-stack)
                     (into [interceptor] leave-stack))
              (do
                (log/error "interceptors depleted without a response")
                response/bad-gateway))))
        response/not-found)
      (catch Exception e
        (log/error e "failed to process request" req)
        response/bad-gateway))))
