;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.rules
  (:require [aero.core :as aero]
            [org.bdinetwork.connector.interceptors :as interceptors]))

(defmethod aero/reader 'b64
  [_ _ value]
  (-> (java.util.Base64/getEncoder)
      (.encodeToString (.getBytes value "UTF-8"))))

(defmethod aero/reader 'rx
  [_ _ value]
  (re-pattern value))

(defn- parse-interceptors [rule]
  (update rule :interceptors #(mapv interceptors/->interceptor %)))

(defn- parse-rules [rules-file]
  (update rules-file :rules #(mapv parse-interceptors %)))

(defn read-rules-file
  "Read rules file and parse rules and interceptors."
  [file]
  (-> file aero/read-config parse-rules))
