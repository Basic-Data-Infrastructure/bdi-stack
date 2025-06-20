;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.interceptors
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [org.bdinetwork.gateway.eval :as eval]
            [org.bdinetwork.gateway.oauth2 :as oauth2]
            [org.bdinetwork.gateway.response :as response]
            [org.bdinetwork.gateway.reverse-proxy :as reverse-proxy])
  (:import (java.net URL)
           (java.util UUID)
           (org.slf4j MDC)))

(defn interceptor [& {:keys [name enter leave error]}]
  {:pre [name (or enter leave error)]}
  (cond-> {:name  name, :enter identity, :leave identity, :error identity}
    enter (assoc :enter enter)
    leave (assoc :leave leave)
    error (assoc :error error)))

(defmulti ->interceptor (fn [[id & _] & _] id))



(defn- log-response
  [{:keys [response trace-id ::logger-enter-ctm] :as ctx}]
  (assoc ctx :response
         (d/let-flow [{:keys [status]} response]
           (log/infof "[%s] Status: %d (duration %dms)"
                      trace-id
                      status
                      (- (System/currentTimeMillis) logger-enter-ctm))
           response)))

(defn with-diagnostics [[k & keys] vars f]
  (if k
    (with-open [_ (MDC/putCloseable (str k) (str (get vars k)))]
      (with-diagnostics keys vars f))
    (f)))

(defmethod ->interceptor 'logger
  [[id & more] & _]

  (interceptor
   :name (str id)
   :doc "Log incoming requests and response status and duration at `info` level."
   :enter
   (fn [{{:keys [request-method scheme server-name server-port uri protocol]} :request
         :keys [vars]
         :as ctx}]
     (let [trace-id (UUID/randomUUID)]
       (with-diagnostics more vars
         #(log/infof "[%s] %s %s://%s:%d%s %s"
                     trace-id
                     (string/upper-case (name request-method))
                     (name scheme)
                     server-name
                     server-port
                     uri
                     protocol))
       (assoc ctx
              :trace-id trace-id
              ::logger-enter-ctm (System/currentTimeMillis))))

   :leave log-response
   :error log-response))

(defmethod ->interceptor 'respond
  [[id response] & _]
  (interceptor
   :name (str id " " (pr-str response))
   :doc  "Respond with given value."
   :enter
   (fn [ctx] (assoc ctx :response response))))


(defmethod ->interceptor 'request/rewrite
  [[id url] & _]
  (let [url    (URL. url)
        scheme (keyword (.getProtocol url))
        host   (.getHost url)
        port   (.getPort url)
        port   (if (= -1 port) (.getDefaultPort url) port)]
    (when-not (#{"/" ""} (.getFile url))
      (throw (ex-info "Only URL without path or query string allowed"
                      {:interceptor id, :url url})))

    (interceptor
     :name (str id " " url)
     :doc  "Rewrite server part of request."
     :enter
     (fn [ctx]
       (-> ctx
           (assoc-in [:request :scheme] scheme)
           (assoc-in [:request :server-name] host)
           (assoc-in [:request :server-port] port)
           (assoc-in [:request :headers "host"] (str host ":" port)))))))

(def eval-env
  {'assoc       assoc
   'assoc-in    assoc-in
   'get         get
   'merge       merge
   'select-keys select-keys
   'str         str
   'update      update})

(defmethod ->interceptor 'request/update
  [[id & form] & _]
  (interceptor
   :name (str id " " (pr-str form))
   :doc  "Update the incoming request using eval on the request object."
   :enter
   (fn  [{:keys [request vars] :as ctx}]
     (let [form (list* (first form) 'request (drop 1 form))]
       (assoc ctx :request
              (eval/evaluate form (-> eval-env
                                      (merge vars)
                                      (assoc 'request request))))))))

(defmethod ->interceptor 'response/update
  [[id & form] & _]
  (interceptor
   :name (str id " " (pr-str form))
   :doc  "Update the outgoing request using eval on the response object."
   :args form
   :leave
   (fn response-eval-leave [{:keys [request response vars] :as ctx}]
     (let [form (list* (first form) 'response (drop 1 form))]
       (assoc ctx :response
              (d/let-flow [response response]
                (eval/evaluate form (-> eval-env
                                        (merge vars)
                                        (assoc 'request request
                                               'response response)))))))))

(defmethod ->interceptor 'reverse-proxy/forwarded-headers
  [[id] & _]
  (interceptor
   :name (str id)
   :doc  "Add `x-forwarded-proto`, `x-forwarded-host` and `x-forwarded-port` headers to request for backend.

  The allows the backend to create local redirects and set domain
  cookies."
   :enter (fn forwarded-headers-enter
            [{{{:strs [host
                       x-forwarded-proto
                       x-forwarded-host
                       x-forwarded-port]} :headers
               :keys                      [scheme server-port]} :request
              :as                                               ctx}]
            (let [proto (or x-forwarded-proto (name scheme))
                  host  (or x-forwarded-host host)
                  port  (or x-forwarded-port server-port)]
              (cond-> ctx
                proto
                (assoc-in [:proxy-request-overrides :headers "x-forwarded-proto"] proto)

                host
                (assoc-in [:proxy-request-overrides :headers "x-forwarded-host"] host)

                port
                (assoc-in [:proxy-request-overrides :headers "x-forwarded-port"] (str port)))))))

(defmethod ->interceptor 'reverse-proxy/proxy-request
  [[id] & _]
  (interceptor
   :name (str id)
   :doc  "Execute proxy request and populate response."
   :enter
   (fn proxy-request-enter
     [{:keys [request proxy-request-overrides trace-id] :as ctx}]
     (assoc ctx :response
            (d/catch
                (reverse-proxy/proxy-request (merge-with merge request proxy-request-overrides))
                (fn proxy-handler-catch [e]
                  (log/error e (str "Failed to launch request"
                                    (when trace-id (str " [" trace-id "]")))
                             request)
                  response/service-unavailable))))))

;; TODO logging / audit
(defmethod ->interceptor 'oauth2/bearer-token
  [[id {:keys [iss] :as requirements} auth-params] & _]
  {:pre [iss (seq auth-params)]}
  (interceptor
   :name (str id " " iss)
   :doc "Require and validate OAUTH2 bearer token.  Responds with 401
   Unauthorized when the token is missing or invalid."
   :enter
   (let [requirements (assoc requirements :jwks-cache-atom (atom {}))]
     (fn oauth2-bearer-token-enter [{:keys [request] :as ctx}]
       (let [auth-header      (get-in request [:headers "authorization"])
             [_ bearer-token] (and auth-header (re-matches #"Bearer (\S+)" auth-header))
             auth-params      (->> auth-params
                                   (map (fn [[k v]] (str (name k) "=\"" v "\"")))
                                   (string/join ", " ))]
         (if (nil? bearer-token)
           (assoc ctx :response
                  (-> response/unauthorized
                      (assoc-in [:headers "www-authenticate"]
                                (str "Bearer " auth-params))))
           (try
             (let [claims (oauth2/decode-access-token bearer-token requirements)]
               (assoc-in ctx [:vars 'oauth2/claims] claims))
             (catch Exception e
               (let [msg (.getMessage e)]
                 (assoc ctx :response
                        (-> response/unauthorized
                            (assoc-in [:headers "www-authenticate"]
                                      (str "Bearer " auth-params
                                           ", error=\"invalid_token\""
                                           ", error_description=\"" msg "\""))
                            (assoc :body msg))))))))))))
