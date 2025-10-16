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
            [org.bdinetwork.service-commons.config :as config]))

(def opt-specs
  (assoc config/server-party-opt-specs
         :association-server-id    ["Association Server id" :str]
         :association-server-url   ["Assocation Server url" ]
         :hostname                 ["Server hostname" :str :default "localhost"]
         :port                     ["Server HTTP Port" :int :default 8080]
         :policies-directory       ["Directory to store policy data, using datascript store" :str :default nil]
         :policies-db-user         ["User for psql policy store" :str :default nil :in [:dbspec :user]]
         :policies-db-hostname     ["Hostname for psql policy store" :str :default nil :in [:dbspec :hostname]]
         :policies-db-port         ["Port for psql policy store" :int :default 5432 :in [:dbspec :port]]
         :policies-db-password     ["Password for psql policy store" :str :default nil :in [:dbspec :password]]
         :policies-db-dbname       ["Database name for psql policy store" :str :default nil :in [:dbspec :dbname]]
         :access-token-ttl-seconds ["Access token time to live in seconds" :int :default 600]))

(defn config
  [env opts-spec]
  (let [c (config/config env opts-spec)]
    (when-not (or (:policies-directory c) (:policies-db-dbname c))
      (throw (ex-info "POLICIES_DIRECTORY or POLICIES_DB_DBNAME must be set" c)))
    c))

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
