;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.rules
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
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



(defmulti rule->interceptor (fn [x] (if (vector? x) [(first x) '..] x)))

(defmethod rule->interceptor '[request/eval ..]
  [[_ & form]]
  {:enter
   (fn request-eval-enter [{:keys [request eval-env] :as ctx}]
     (log/debug "request-eval-enter" form)
     (let [form (list* (first form) 'request (drop 1 form))]
       (assoc ctx :request
              (evaluate form (assoc eval-env 'request request)))))})

(defmethod rule->interceptor '[response/eval ..]
  [[_ & form]]
  {:leave
   (fn response-eval-leave [{:keys [request response eval-env] :as ctx}]
     (log/debug "response-eval-leave" form)
     (let [form (list* (first form) 'response (drop 1 form))]
       (assoc ctx :response
              (d/let-flow [response response]
                (evaluate form (assoc eval-env
                                      'request request
                                      'response response))))))})

(defmethod rule->interceptor 'reverse-proxy/forwarded-headers
  [_]
  reverse-proxy/forwarded-headers-interceptor)

(defmethod rule->interceptor 'reverse-proxy/proxy-request
  [_]
  reverse-proxy/proxy-request-interceptor)

(defn parse-interceptors [rule]
  (update rule :interceptors #(map rule->interceptor %)))

(defn parse-rules [rules-file]
  (update rules-file :rules #(map parse-interceptors %)))

(defmethod envopts/parse :rules-file
  [s _]
  [(-> s io/file aero/read-config parse-rules)])
