;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.gateway
  (:require [aleph.http :as http]
            [clojure.tools.logging :as log]
            [org.bdinetwork.connector.response :as response]
            [org.bdinetwork.connector.rules :as rules]))

(defn find-rule [rules _req]
  ;; TODO
  (first rules))

(def eval-env
  {'assoc       assoc
   'get         get
   'merge       merge
   'select-keys select-keys
   'str         str
   'update      update})

(defn make-gateway [{:keys [vars rules]}]
  (let [env (merge eval-env vars)]
    (fn gateway [req]
      (try
        (if-let [{:keys [pipeline]} (find-rule rules req)]
          (-> (reduce (fn [r action] (rules/apply-action r env action))
                      {:incoming {:request req}
                       :outgoing {:request req}}
                      pipeline)
              :outgoing
              :response)
          response/not-found)
        (catch Exception e
          (log/error e "failed to process request" req)
          response/bad-gateway)))))

(comment
  (require '[clojure.java.io :as io])
  (require '[aero.core :as aero])
  (def s
    (http/start-server (make-gateway (-> "rules.edn" io/file aero/read-config))
                       {:port             8081
                        :shutdown-timeout 0}))
  (doto s .close .wait-for-close)
  )
