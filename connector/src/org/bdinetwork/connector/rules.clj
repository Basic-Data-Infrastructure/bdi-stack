;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.rules
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [manifold.deferred :as d]
            [nl.jomco.envopts :as envopts]
            [org.bdinetwork.connector.eval :refer [evaluate]]
            [org.bdinetwork.connector.reverse-proxy :as reverse-proxy]))

(defmethod aero/reader 'b64
  [_ _ value]
  (-> (java.util.Base64/getEncoder)
      (.encodeToString (.getBytes value "UTF-8"))))

(defmethod aero/reader 'rx
  [_ _ value]
  (re-pattern value))

(defmethod envopts/parse :rules
  [s _]
  [(-> s io/file aero/read-config)])

(defmulti apply-action (fn [_r _vars [oper]] oper))

(defmethod apply-action 'request/eval
  [{{req :request} :outgoing :as r} vars [_ & form]]
  {:pre [req]}
  (let [form (list* (first form) 'request (drop 1 form))
        env  (assoc vars 'request req)]
    (assoc-in r [:outgoing :request] (evaluate form env))))

(defmethod apply-action 'response/eval
  [{{res :response} :outgoing :as r} vars [_ & form]]
  {:pre [res]}
  (assoc-in r [:outgoing :response]
            (d/let-flow [res res]
              (let [form (list* (first form) 'response (drop 1 form))
                    env  (assoc vars 'response res)]
                (evaluate form env)))))

(defmethod apply-action 'bdi/authenticate
  [r _ _]
  r)

(defmethod apply-action 'reverse-proxy
  [r _ _]
  (reverse-proxy/handler r))
