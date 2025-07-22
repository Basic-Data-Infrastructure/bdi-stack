;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.interceptors
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [nl.jomco.http-status-codes :as http-status]
            [org.bdinetwork.gateway.eval :as eval]
            [org.bdinetwork.gateway.oauth2 :as oauth2]
            [org.bdinetwork.gateway.response :as response]
            [org.bdinetwork.gateway.reverse-proxy :as reverse-proxy])
  (:import (java.net URL)
           (org.slf4j MDC)))

(defn interceptor [& {:keys [name args enter leave error]}]
  {:pre [name (or enter leave error)]}
  (cond-> {:name  name, :args args}
    enter (assoc :enter enter)
    leave (assoc :leave leave)
    error (assoc :error error)))

(defmulti ->interceptor (fn [[id & _] & _] id))



(def eval-env
  {'assoc          assoc
   'assoc-in       assoc-in
   'dissoc         dissoc
   'get            get
   'get-in         get-in
   'merge          merge
   'select-keys    select-keys
   'update         update
   'update-in      update-in
   'str            str
   'str/replace    string/replace
   'str/lower-case string/lower-case
   'str/upper-case string/upper-case
   '=              =
   'not            not})

(defn- mk-eval-env [{:keys [vars request response] :as ctx}]
  (cond-> (-> eval-env
              (merge vars)
              (assoc 'ctx ctx, 'request request))
    ;; TODO: Response should never be deferrable in this context!
    (and response (not (d/deferrable? response)))
    (assoc 'response response)))

(defn- evaluate [ctx expr]
  (when expr
    (eval/evaluate expr (mk-eval-env ctx))))

(defn exec
  "Executed interceptor for `phase` with given `ctx` and evaluated `args`."
  [phase {:keys [args] :as interceptor} ctx]
  {:pre [(#{:enter :leave :error} phase)]}
  (if-let [handler (get interceptor phase)]
    (apply handler
           ctx
           (mapv (partial evaluate ctx) args))
    ctx))



(defn- with-diagnostics [[[k v] & props] f]
  (if k
    (with-open [_ (MDC/putCloseable (str k) (pr-str v))]
      (with-diagnostics props f))
    (f)))

(defn- logger-leave
  [{:keys              [response ::logger-enter-ctm]
    :as                ctx
    {:keys [request-method
            scheme
            server-name
            server-port
            uri
            query-string
            protocol]} ::logger-original-request}
   props]
  (let [status (:status response)
        duration (- (System/currentTimeMillis) logger-enter-ctm)]
    (with-diagnostics (into [] props)
      #(log/infof "%s %s://%s:%d%s%s %s / %d %s / %dms"
                  (string/upper-case (name request-method))
                  (name scheme)
                  server-name
                  server-port
                  uri
                  (if query-string (str "?" query-string) "")
                  protocol
                  status
                  (http-status/->description status)
                  duration)))
  ctx)

(def ^:private logger-error logger-leave)

(defmethod ->interceptor 'logger
  [[id props-expr] & _]
  {:pre [(or (nil? props-expr)
             (and (map? props-expr)
                  (not (some (complement string?) (keys props-expr)))))]}

  (interceptor
   :name (str id)
   :args [props-expr]
   :doc "Log incoming requests and response status and duration at `info` level."
   :enter
   (fn logger-enter [{:keys [request] :as ctx} & _]
     (assoc ctx
            ::logger-original-request request
            ::logger-enter-ctm (System/currentTimeMillis)))

   :leave logger-leave
   :error logger-error))

(defmethod ->interceptor 'respond
  [[id response-expr] & _]
  (interceptor
   :name (str id " " (pr-str response-expr))
   :args [response-expr]
   :doc  "Respond with given value from evaluated expression."
   :enter
   (fn respond-enter [ctx response]
     (assoc ctx :response response))))

(defmethod ->interceptor 'request/rewrite
  [[id url-expr] & _]
  (interceptor
   :name (str id " " url-expr)
   :args [url-expr]
   :doc  "Rewrite server part of request."
   :enter
   (fn request-rewrite-enter [ctx url]
     (let [url    (URL. url)
           scheme (keyword (.getProtocol url))
           host   (.getHost url)
           port   (.getPort url)
           port   (if (= -1 port) (.getDefaultPort url) port)]
       (when-not (#{"/" ""} (.getFile url))
         (throw (ex-info "Only URL without path or query string allowed"
                         {:interceptor id, :url url})))
       (-> ctx
           (assoc-in [:request :scheme] scheme)
           (assoc-in [:request :server-name] host)
           (assoc-in [:request :server-port] port)
           (assoc-in [:request :headers "host"] (str host ":" port)))))))

(defmethod ->interceptor 'request/update
  [[id & expr] & _]
  (interceptor
   :name (str id " " (pr-str expr))
   :args expr
   :doc  "Update the incoming request using eval on the request object."
   :enter
   (fn request-update-enter [{:keys [request] :as ctx} & expr]
     (assoc ctx :request (apply (first expr) request (drop 1 expr))))))

(defmethod ->interceptor 'response/update
  [[id & expr] & _]
  (interceptor
   :name (str id " " (pr-str expr))
   :args expr
   :doc  "Update the outgoing request using eval on the response object."
   :args expr
   :leave
   (fn response-update-leave [ctx & expr]
     (apply update ctx :response expr))))

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
     ;; reverse-proxy/proxy-request returns a derreable response
     ;; but interceptors return a deferrable ctx instead
     (d/catch
         (d/chain (merge-with merge request proxy-request-overrides)
                  reverse-proxy/proxy-request
                  #(assoc ctx :response %))
         (fn proxy-handler-catch [e]
           (log/error e (str "Failed to launch request"
                             (when trace-id (str " [" trace-id "]")))
                      request)
           (assoc ctx :response response/service-unavailable))))))

(defmethod ->interceptor 'oauth2/bearer-token
  [[id requirements-expr auth-params-expr] & _]
  (interceptor
   :name (str id " " auth-params-expr)
   :args [requirements-expr auth-params-expr]
   :doc "Require and validate OAUTH2 bearer token.  Responds with 401
   Unauthorized when the token is missing or invalid."
   :enter
   (let [jwks-cache-atom (atom {})]
     (fn oauth2-bearer-token-enter [{:keys [request] :as ctx} requirements auth-params]
       (let [requirements     (assoc requirements
                                     :jwks-cache-atom jwks-cache-atom)
             auth-header      (get-in request [:headers "authorization"])
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
             (let [claims (oauth2/unsign-access-token bearer-token requirements)]
               (assoc ctx :oauth2/bearer-token-claims claims))
             (catch Exception e
               (let [msg (.getMessage e)]
                 (assoc ctx :response
                        (-> response/unauthorized
                            (assoc-in [:headers "www-authenticate"]
                                      (str "Bearer " auth-params
                                           ", error=\"invalid_token\""
                                           ", error_description=\"" msg "\""))
                            (assoc :body msg))))))))))))

(defn echo
  [{{{:strs [delay] :or {delay "0"}} :params :as request} :request :as ctx}]
  (d/future
    (Thread/sleep (* 1000 (parse-long delay)))
    (assoc ctx :response (-> response/ok
                             (assoc :body (prn-str request))))))

(defmethod ->interceptor 'test/echo
  [[id] & _]
  (interceptor :name id
               :args []
               :doc "Return request as edn response"
               :enter #'echo))

(def ^:private single-byte-array
  (byte-array [(int \.)]))

(defn- single-byte-buffer
  "Return a java nio ByteBuffer containing a single byte."
  []
  (java.nio.ByteBuffer/wrap single-byte-array))

(defn drip-stream
  "Create a manifold stream that slowly drips data.

  `count` specifies the number of bytes to generare.
  `interval` is the number of seconds between generated bytes."
  [count interval]
  (let [s (s/stream 1)]
    (async/go
      (loop [count count]
        (async/<! (async/timeout (Math/round (* 1000.0 interval))))
        (cond
          (not (s/put! s (single-byte-buffer)))
          ;; failed to put byte (buffer full), retry
          (recur count)

          (pos? count)
          (recur (dec count))

          :else
          (s/close! s))))
    s))

(defn drip
  [{{{:strs [delay count interval] :or {delay "0" interval "2" count "10"}} :params} :request :as ctx}]
  (let [delay (parse-double delay)
        count (parse-long count)
        interval (parse-double interval)]
    (d/future
      (Thread/sleep (Math/round (* 1000 delay)))
      (assoc ctx :response (-> response/ok
                               (assoc :body (drip-stream count interval)))))))

(defmethod ->interceptor 'test/drip
  [[id] & _]
  (interceptor :name id
               :args []
               :doc "Slowly stream a response"
               :enter #'drip))
