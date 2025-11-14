;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.main
  (:gen-class)
  (:require [environ.core :refer [env]]
            [nl.jomco.resources :refer [with-resources wait-until-interrupted]]
            [org.bdinetwork.connector.system :as system]
            [passage.main :as passage]))

(defn start!
  [env]
  (system/run-system (passage/config env passage/opt-specs)))

(defn -main [& _]
  (with-resources [_sys (start! env)]
    (wait-until-interrupted)))
