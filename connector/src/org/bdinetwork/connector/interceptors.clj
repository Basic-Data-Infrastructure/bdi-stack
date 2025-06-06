;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors
  (:require [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.authentication.access-token :as access-token]
            [org.bdinetwork.gateway.interceptors :refer [->interceptor interceptor]]))

(defn extract-client-id [request config]
  (let [auth (get-in request [:headers "authorization"])]
    (when-let [[_ token] (and auth (re-matches #"Bearer (\S+)" auth))]
      (try
        (access-token/access-token->client-id token config)
        (catch Exception e
          (log/info e "Invalid access token")
          nil)))))

(defmethod ->interceptor 'bdi/authenticate
  [[id] {:keys [server-id] :as config}]
  (interceptor
   :name (str id " " server-id)
   :doc "Enforce BDI authentication on incoming requests and add
   \"x-bdi-client-id\" request header.  Responds with 401 Unauthorized
   when request is not allowed."
   :enter
   (fn [{:keys [request] :as ctx}]
     (if-let [client-id (extract-client-id request config)]
       (assoc-in ctx [:request :headers "x-bdi-client-id"] client-id)
       (assoc ctx :response
              {:status  http-status/unauthorized
               :headers {"www-authenticate" "Bearer scope=\"BDI\""}})))))

(defmethod ->interceptor 'bdi/deauthenticate
  [[id] _]
  (interceptor
   :name (str id)
   :doc "Remove \"x-bdi-client-id\" request header for avoid clients
   from fooling backend into being authenticated."
   :enter
   (fn [ctx]
     (update-in ctx [:request :headers] dissoc "x-bdi-client-id"))))
