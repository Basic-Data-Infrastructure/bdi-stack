;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [environ.core :refer [env]]
            [nl.jomco.envopts :as envopts]
            [nl.jomco.resources :refer [wait-until-interrupted with-resources]]
            [org.bdinetwork.connector.system :as system]
            [org.bdinetwork.service-commons.config :refer [config server-party-opt-specs]]))

(def opt-specs
  (merge server-party-opt-specs
         {:rules-file ["EDN file specifying request processing rules" :file]
          :hostname   ["Server hostname" :str :default "localhost"]
          :port       ["Server HTTP Port" :int :default 8081]

          :association-server-id    ["Association Server id" :str]
          :association-server-url   ["Assocation Server url" :str]
          :access-token-ttl-seconds ["Access token time to live in seconds" :int :default 600]}))

(defmethod envopts/parse :file
  [s _opt-spec]
  (let [file (io/file s)]
    (if (.exists file)
      [file]
      [nil "file does not exist"])))

(defn start
  [env]
  (system/run-system (config env opt-specs)))

(defn -main [& _]
  (with-resources [_system (start env)]
    (wait-until-interrupted)))
