;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.assocation-register.system
  (:require [buddy.core.keys :refer [private-key]]
            [clojure.string :as string]
            [org.bdinetwork.assocation-register.data-source :as ds]
            [org.bdinetwork.assocation-register.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))

(defprotocol Resource
  "Protocol defining a closeable resource.

  Resources are opened items that must be closed. Any
  java.io.Closeable is a resource.

  Components that are started and should be stopped can extend
  Resource (via metadata or using extend-protocol) to implement the
  `close` method."
  :extend-via-metadata true
  (close [resource] "Close resource."))

;; resources
;;
;; - must be closed in reverse order of opening
;;
;; - must be closed even if exceptions occur (even when closing other
;;   resources) -- implies (try .. finally)
;;
;; - can be opened, used, closed explicitly (as in with-open)
;;
;; - need direct access to resource (makes wrapping objects
;;   inconvenient)
;;
;; - can compose -- encapsulate construction/start and closed -- TBD?
;;
;; - dependency injection conflicts with encapsulation -- should a
;;   provided dependency be closed when dependent resource is closed?
;;   -- probably not.
;;
;; - when encapsualated resources are started this may fail and they
;;   should be closed. But if this succeeds... looks like RAII / ref
;;   counting destructors.
;;
;; x (f...)
;; y (g ..)
;; z (f x y)
;;
;; - threads? What if I want to start a service in a REPL? I want to
;;   do this in a separate thread, and be able to stop the service
;;   from the repl. I can't easily use with-open in this case...
;;
;;  (def something (open! ...))
;;
;;   ...
;;
;;  (close something)
;;
;; Maybe we should distinguish resources (that can be local bindings
;; and be closed automatically) from services (that maybe should be
;; registered for REPL? -- make sure you can't forget to bind a
;; service to a global?
;;
;; What if we define a defresource that closes the resource when
;; rebound? this should also deal with cases when namespace was
;; deleted (see mount)
;;
;;
;;

(extend-protocol Resource
  java.lang.Object
  (close [x])

  java.io.Closeable
  (close [resource]
    (.close resource))

  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(defmacro with-resources
  [[sym x & rest-bindings :as bindings] & body]
  (if (seq bindings)
    `(let [~sym ~x]
       (try (with-resources [~@rest-bindings]
              ~@body)
            (finally (close ~sym))))
    `(do ~@body)))

;; TODO: provide "subresources" to close instead of close-fn
(defn closeable
  [x close-fn]
  (with-meta x {`close close-fn}))

(defmacro defresource
  [& args]
  (let [symbol (first args)]
    `(do (when-let [old (resolve '~symbol)]
           (close old))
         (def ~@args))))

(defn run-system
  [{:keys [data-source] :as config}]
  (let [handler (web/make-handler data-source config)]
    (run-jetty handler {:join? false :port (:port config) :hostname (:hostname config)})))
