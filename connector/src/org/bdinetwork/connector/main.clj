;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.main
  (:gen-class)
  (:require [environ.core :refer [env]]
            [nl.jomco.resources :refer [wait-until-interrupted with-resources]]
            [org.bdinetwork.connector.system :as system]
            [org.bdinetwork.service-commons.config :refer [config]]))

(def opt-specs
  {:rules-file ["EDN file specifying request processing rules"]
   :hostname   ["Server hostname" :str :default "localhost"]
   :port       ["Server HTTP Port" :int :default 8081]})

(defn start
  [env]
  (system/run-system (config env opt-specs)))

(defn -main [& _]
  (with-resources [_system (start env)]
    (wait-until-interrupted)))
