;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns user
  (:require [org.bdinetwork.authorization-register.main :as main]
            [org.bdinetwork.authorization-register.system :as system]
            [environ.core :refer [env]]
            [nl.jomco.resources :refer [defresource close]]))

(def config
  (try (main/config env)
       (catch Exception e
         (prn e))))

(defresource system)

(defn start!
  []
  (defresource system
    (system/run-system config)))

(defn stop!
  []
  (close system))
