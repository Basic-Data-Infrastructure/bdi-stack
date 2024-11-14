;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.authorization-register.main
  (:gen-class)
  (:require [buddy.core.keys :as keys]
            [environ.core :refer [env]]
            [nl.jomco.envopts :as envopts]
            [nl.jomco.resources :refer [close]]
            [org.bdinetwork.authorization-register.system :as system]))

(def opt-specs
  {:private-key              ["Server private key pem file" :private-key]
   :public-key               ["Server public key pem file" :public-key]
   :x5c                      ["Server certificate chain pem file" :x5c]
   :association-server-id    ["Association Server id" :str]
   :association-server-url   ["Assocation Server url" ]
   :server-id                ["Server ID (EORI)" :str]
   :hostname                 ["Server hostname" :str :default "localhost"]
   :port                     ["Server HTTP Port" :int :default 8080]
   :policies-db              ["Directory to store policy data" :str]
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

(defn wait-until-interrupted
  []
  (loop []
    (when-not (try (Thread/sleep 10000)
                   false
                   (catch InterruptedException _
                     true))
      (recur))))

(defn config
  [env]
  (let [[config errs] (envopts/opts env opt-specs)]
    (when errs
      (throw (ex-info  (str "Error in environment configuration\n"
                            (envopts/errs-description errs) "\n"
                            "Available environment vars:\n"
                            (envopts/specs-description opt-specs) "\n")
                       {:errs errs
                        :config config})))
    config))

(defn start
  [env]
  (system/run-system (config env)))

(defn -main [& _]
  (let [system (start env)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (close system)
                                 (shutdown-agents))))
    (wait-until-interrupted)))
