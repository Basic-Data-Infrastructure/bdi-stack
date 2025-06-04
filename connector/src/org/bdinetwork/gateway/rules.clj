;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.rules
  (:require [aero.core :as aero]
            [org.bdinetwork.gateway.interceptors :as interceptors]))

(defmethod aero/reader 'b64
  [_ _ value]
  (-> (java.util.Base64/getEncoder)
      (.encodeToString (.getBytes value "UTF-8"))))

(defmethod aero/reader 'rx
  [_ _ value]
  (re-pattern value))

(defn- parse-interceptors [rule & args]
  (update rule :interceptors
          (fn [specs]
            (mapv #(apply interceptors/->interceptor % args)
                  specs))))

(defn- parse-rules [rules-file & args]
  (update rules-file :rules
          (fn [rules]
            (mapv #(apply parse-interceptors % args)
                  rules))))

(defn read-rules-file
  "Read rules file and parse rules and interceptors."
  [file & args]
  (apply parse-rules (-> file aero/read-config) args))
