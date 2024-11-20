;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.remote-association
  "Implement org.bdinetwork.ring.association.Assocation protocol by querying a BDI Assocation Register."
  (:require [clojure.walk :as walk]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.interceptors :as interceptors]
            [org.bdinetwork.ishare.client.request :as request]
            [org.bdinetwork.ring.association :refer [Association]]))

(defn ensure-ok
  [{:keys [status] :as response}]
  (when-not (= 200 status)
    (throw (ex-info (str "Unexpected status code '" status "' from association register")
                    (update response :request interceptors/redact-request))))
  response)

(defrecord RemoteAssociation [client-data]
  Association
  (party [_ party-id]
    (-> client-data
        (request/party-request party-id)
        (client/exec)
        ensure-ok
        :ishare/result
        (walk/stringify-keys)
        (get "party_info")))
  (trusted-list [_]
    (-> client-data
        (request/trusted-list-request)
        (client/exec)
        ensure-ok
        :ishare/result
        (walk/stringify-keys)
        (get "trusted_list"))))

(defn remote-association
  [{:ishare/keys [client-id x5c private-key satellite-id satellite-base-url]
    :as client-data}]
  {:pre [client-id x5c private-key satellite-id satellite-base-url]}
  (->RemoteAssociation client-data))
