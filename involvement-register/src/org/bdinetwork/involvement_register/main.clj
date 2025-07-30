;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.involvement-register.main
  (:gen-class)
  (:require [environ.core :refer [env]]
            [nl.jomco.envopts :as envopts]
            [nl.jomco.resources :refer [wait-until-interrupted with-resources]]))

(def opt-specs
  (assoc :hostname                 ["Server hostname" :str :default "localhost"]
         :port                     ["Server HTTP Port" :int :default 5005]))

(defn start
  [env]
  (system/run-system (envopts/opts! env opt-specs)))

(defn -main [& _]
  (with-resources [#_:clj-kondo/ignore system (start env)]
    (wait-until-interrupted)))
