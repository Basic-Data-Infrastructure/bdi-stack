;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.rules
  (:require [aero.core :as aero]
            [buddy.core.keys :as keys]
            [org.bdinetwork.service-commons.config :as config]
            [passage.rules :as passage :refer [*default-aliases*]]))

;; load bdi specific interceptors
(require '[org.bdinetwork.connector.interceptors])

;; insert pem readers
(defmethod aero/reader 'private-key
  [_ _ file-name]
  (keys/private-key file-name))

(defmethod aero/reader 'public-key
  [_ _ file-name]
  (keys/public-key file-name))

(defmethod aero/reader 'x5c
  [_ _ file-name]
  (config/split-x5c file-name))

(defn read-rules-file
  "Read rules file and parse rules and interceptors."
  [file]
  (binding [*default-aliases* '{bdi org.bdinetwork.connector.interceptors}]
    (passage/read-rules-file file)))
