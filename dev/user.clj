;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Jomco BV
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remcojomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns user
  (:require [org.bdinetwork.authorization-register.main :as main]
            [org.bdinetwork.authorization-register.system :as system]
            [nl.jomco.resources :refer [defresource close]]))

(def config (main/config))

(defresource system)

(defn start!
  []
  (defresource system
    (system/run-system config)))

(defn stop!
  []
  (close system))
