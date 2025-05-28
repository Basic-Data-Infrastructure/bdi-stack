;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.gateway
  (:require [clojure.tools.logging :as log]
            [org.bdinetwork.connector.matcher :as matcher]
            [org.bdinetwork.connector.response :as response]))

(defn find-rule [rules req]
  (loop [rules rules]
    (when-let [{:keys [match] :as rule} (first rules)]
      (if-let [vars (matcher/match match req)]
        (update rule :vars merge vars)
        (recur (next rules))))))

(def eval-env
  {'assoc       assoc
   'get         get
   'merge       merge
   'select-keys select-keys
   'str         str
   'update      update})

(defn make-gateway [{:keys [vars rules]}]
  (fn gateway [req]
    (try
      (if-let [{:keys [interceptors] :as rule} (find-rule rules req)]
        (loop [{:keys [response] :as ctx} {:request req
                                           :eval-env (merge eval-env
                                                            vars
                                                            (:vars rule))}
               enter                      interceptors
               leave                      []]
          (if response
            (if-let [interceptor (first leave)]
              (recur ((or (:leave interceptor) identity) ctx)
                     nil
                     (next leave))
              response)
            (if-let [interceptor (first enter)]
              (recur ((or (:enter interceptor) identity) ctx)
                     (next enter)
                     (into [interceptor] leave))
              (do
                (log/error "interceptors depleted without a response")
                response/bad-gateway))))
        response/not-found)
      (catch Exception e
        (log/error e "failed to process request" req)
        response/bad-gateway))))
