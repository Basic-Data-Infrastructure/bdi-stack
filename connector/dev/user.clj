;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(in-ns 'clojure.core)

(defn pk
  "Peek value for debugging."
  ([v] (prn v) v)
  ([k v] (prn k v) v))

(defn pk->
  "Peek value for debugging."
  ([v] (prn v) v)
  ([v k] (prn k v) v))

(ns user
  (:require [nl.jomco.resources :refer [defresource close]]
            [org.bdinetwork.connector.system :as connector.system]))

#_:clj-kondo/ignore
(defresource system)

(defn start! []
  #_:clj-kondo/ignore
  (defresource system
    (connector.system/run-system {:rules-file       "rules.edn"
                                  :hostname         "localhost"
                                  :port             8081
                                  :shutdown-timeout 0})))

(defn stop! []
  (close system))
