;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.main
  (:gen-class)
  (:require [environ.core :refer [env]]
            [nl.jomco.resources :refer [close wait-until-interrupted]]
            [org.bdinetwork.authorization-register.system :as system]
            [org.bdinetwork.service-commons.config :refer [config server-party-opt-specs]]))

(def opt-specs
  (assoc server-party-opt-specs
         :association-server-id    ["Association Server id" :str]
         :association-server-url   ["Assocation Server url" ]
         :hostname                 ["Server hostname" :str :default "localhost"]
         :port                     ["Server HTTP Port" :int :default 8080]
         :policies-db              ["Directory to store policy data" :str]
         :access-token-ttl-seconds ["Access token time to live in seconds" :int :default 600]))

(defn start
  [env]
  (system/run-system (config env opt-specs)))

(defn -main [& _]
  (let [system (start env)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (close system)
                                 (shutdown-agents))))
    (wait-until-interrupted)))
