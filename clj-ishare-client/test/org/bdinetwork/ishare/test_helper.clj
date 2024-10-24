;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.test-helper
  (:require [clojure.core.async :as async :refer [alt!!]]
            [org.bdinetwork.ishare.client :as ishare-client]))

(defn <!!-timeout
  ([c description info]
   (let [t (async/timeout 2000)]
     (alt!! c ([x _] x)
            t (throw
               (ex-info (str "Timeout " description)
                        info)))))
  ([c description]
   (<!!-timeout c description {})))

(defn take-request!
  ([c msg]
   (<!!-timeout c msg))
  ([c]
   (take-request! c "taking request")))

(defn >!!-timeout
  [c x description]
  (let [t (async/timeout 2000)]
    (alt!! [[c x]] true
           t (throw (ex-info (str "Timeout " description)
                             x)))))

(defn put-response!
  [c response]
  (>!!-timeout c response "putting response"))

(defn build-client
  "Create a client useable with babashka.http-client/request.

  This client deliveries ring like request map to the given
  bi-directional channel and expects ring like response maps as
  response on the same channel."
  [c]
  (fn [request]
    (>!!-timeout c request "putting request")
    (<!!-timeout c "taking response" {:request request})))

(defn run-exec
  "Run ishare client exec asynchronously returning a channel and a result future.

  The returns channel is bi-directional it delivers ring like request
  map and expects ring like response map."
  [req]
  (let [c (async/chan)]
    [c (binding [ishare-client/http-client (build-client c)]
         (future
           (try
             (let [res (ishare-client/exec req)]
               (async/close! c)
               res)
             (catch Throwable e
               (>!!-timeout c {:exception e} "Exception")
               (async/close! c)))))]))
