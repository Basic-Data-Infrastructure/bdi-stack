;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.system
  (:require [nl.jomco.resources :refer [mk-system]]
            [org.bdinetwork.connector.rules :as rules]
            [passage]
            [passage.system]))

(defn run-system
  [{:keys [rules-file] :as config}]
  (mk-system [gateway (-> rules-file
                          (rules/read-rules-file)
                          (passage/make-gateway))
              _server (passage.system/start-server gateway config)]))
