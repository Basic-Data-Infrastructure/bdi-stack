;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.gateway
  (:require [clojure.tools.logging :as log]
            [org.bdinetwork.connector.response :as response]))

(def eval-env
  {'assoc       assoc
   'get         get
   'merge       merge
   'select-keys select-keys
   'str         str
   'update      update})

(defn find-rule [rules _req]
  ;; TODO
  (first rules))

(defn make-gateway [{:keys [vars rules]}]
  (fn gateway [req]
    (try
      (if-let [{:keys [interceptors]} (find-rule rules req)]
        (loop [ctx   {:request req, :eval-env (merge eval-env vars)}
               enter interceptors
               leave []]
          (if (:response ctx)
            (if-let [interceptor (first leave)]
              (recur ((or (:leave interceptor) identity) ctx)
                     nil
                     (next leave))
              (:response ctx))
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
