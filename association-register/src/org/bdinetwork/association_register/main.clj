;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.main
  (:gen-class)
  (:require [environ.core :refer [env]]
            [nl.jomco.envopts :as envopts]
            [nl.jomco.resources :refer [wait-until-interrupted with-resources]]
            [org.bdinetwork.association-register.system :as system]
            [org.bdinetwork.authentication.in-memory-association :refer [read-source]]
            [org.bdinetwork.service-commons.config :refer [config server-party-opt-specs]]))

(def opt-specs
  (assoc server-party-opt-specs
         :data-source              ["YAML file specifying parties and trusted list" :data-source]
         :hostname                 ["Server hostname" :str :default "localhost"]
         :port                     ["Server HTTP Port" :int :default 9902]
         :access-token-ttl-seconds ["Access token time to live in seconds" :int :default 600]))

(defmethod envopts/parse :data-source
  [s _]
  [(read-source s)])

(defn start
  [env]
  (system/run-system (config env opt-specs)))

(defn -main [& _]
  (with-resources [#_:clj-kondo/ignore system (start env)]
    (wait-until-interrupted)))
