;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.test.noodlebar-helper
  "Implements a minimal Noodlebar server for testing."
  (:require [clojure.data.json :as json]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [Resource]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.time Instant)))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(defn now
  []
  (.getEpochSecond (Instant/now)))

(defn noodlebar-handler
  [{:keys [uri body] :as _req}]
  (case uri
    "/api/authorization/unsigned-delegation"
    (let [mask (get (-> body slurp json/read-str) "delegationRequest")]
      {:status http-status/ok
       :headers {"content-type" "application/json"}
       :body (json/write-str
              (-> mask
                  (assoc "notBefore" (- (now) 30)
                         "notOnOrAfter" (+ (now) 30))
                  (assoc-in ["policySets" 0 "maxDelegationDepth"] 1)
                  (assoc-in ["policySets" 0 "target" "licenses"] ["0001"])))})))

(defn noodlebar-test-server
  [{:keys [host port]}]
  (-> noodlebar-handler
      (run-jetty {:join? false
                  :host  host
                  :port  port})))
