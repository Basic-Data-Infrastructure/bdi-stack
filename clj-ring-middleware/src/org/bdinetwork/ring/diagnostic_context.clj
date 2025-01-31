;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: MIT

(ns org.bdinetwork.ring.diagnostic-context
  (:import
   (org.slf4j MDC)))

(defn as-str
  [s]
  (if (keyword? s)
    (subs (str s) 1) ;; remove leading colon
    (str s)))

(defmacro with-context
  "Evaluate `body` with `kvs` added to the diagnostic context."
  [kvs & body]
  (if (seq kvs)
    (let [[k v] (take 2 kvs)]
      `(with-open [mdc-entry# (MDC/putCloseable (as-str ~k) (as-str ~v))]
         (with-context ~(drop 2 kvs)
           ~@body)))
    `(do ~@body)))

(defn wrap-request-context
  [handler]
  (fn [{:keys [uri request-method remote-addr server-name server-port scheme protocol query-string]
        :as   request}]
    (with-context ["uri" uri
                   "request-method" request-method
                   "remote-addr" remote-addr
                   "server-name" server-name
                   "server-port" server-port
                   "scheme" scheme
                   "protocol" protocol
                   "query-string" query-string]
      (handler request))))
