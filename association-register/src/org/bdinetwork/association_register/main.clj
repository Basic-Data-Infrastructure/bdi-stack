;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.association-register.main
  (:require [org.bdinetwork.association-register.system :as system]
            [org.bdinetwork.service-provider.in-memory-association :refer [read-source]]
            [buddy.core.keys :as keys]
            [nl.jomco.resources :refer [with-resources wait-until-interrupted]]
            [nl.jomco.envopts :as envopts]
            [environ.core :refer [env]])
  (:gen-class))

(def opt-specs
  {:private-key              ["Server private key pem file" :private-key]
   :public-key               ["Server public key pem file" :public-key]
   :x5c                      ["Server certificate chain pem file" :x5c]
   :data-source              ["YAML file specifying parties and trusted list" :data-source]
   :server-id                ["Server ID (EORI)" :str]
   :hostname                 ["Server hostname" :str :default "localhost"]
   :port                     ["Server HTTP Port" :int :default 9902]
   :access-token-ttl-seconds ["Access token time to live in seconds" :int :default 600]})

(defmethod envopts/parse :private-key
  [s _]
  [(keys/private-key s)])

(defmethod envopts/parse :public-key
  [s _]
  [(keys/public-key s)])

(defmethod envopts/parse :x5c
  [s _]
  [(system/x5c s)])

(defmethod envopts/parse :data-source
  [s _]
  [(read-source s)])


(defn config
  [env]
  (let [[config errs] (envopts/opts env opt-specs)]
    (if errs
      (throw (ex-info (str "Error in environment configuration\n"
                           (envopts/errs-description errs) "\n"
                           "Available environment options:\n"
                           (envopts/specs-description opt-specs)
                           "\n")
                      {:config config
                       :errs errs}))
      config)))

(defn start
  [env]
  (system/run-system (config env)))

(defn -main [& _]
  (with-resources [#_:clj-kondo/ignore system (start env)]
    (wait-until-interrupted)))
