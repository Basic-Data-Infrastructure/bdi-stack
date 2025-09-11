;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.cache
  (:require [clojure.core.cache :as cache]
            [nl.jomco.http-status-codes :as http-status])
  (:import java.time.Instant))

(defn- expired?
  [result]
  (and (realized? result)
       (let [expires-at (::expires-at @result)]
         (assert expires-at "Realized item in ExpiresCache needs ::expires-at key")
         (not (.isBefore (Instant/now) expires-at)))))

(defn- prune-cache [cache]
  ;; using reduce/dissoc to ensure that cache is identical when no item is expired
  (reduce-kv (fn [m item result]
               (if (expired? result)
                 (dissoc m item)
                 m))
             cache
             cache))

(cache/defcache ExpiresCache [cache]
  cache/CacheProtocol
  (lookup [_ item]
    (let [result (get cache item)]
      (when-not (expired? result)
        result)))
  (lookup [_ item not-found]
    (let [result (get cache item not-found)]
      (when-not (expired? result)
        result)))
  (has? [_ item]
    (when-let [[_ result] (find cache item)]
      (not (expired? result))))
  (hit [this _item]
    this)
  (miss [_ item result]
    (ExpiresCache. (-> cache
                       (prune-cache)
                       (assoc item result))))
  (evict [_ item]
    (ExpiresCache. (-> cache
                       (prune-cache)
                       (dissoc item))))
  (seed [_ base]
    (ExpiresCache. base))

  Object
  (toString [_] (str cache)))

(defn expires-cache-factory
  "Create a cache object that stores items, using ::expires-at key in the result to determine when the cached result expires."
  []
  (ExpiresCache. {}))

(defn bearer-token-expires-at
  "Derive from response when bearer token expires."
  [{:keys [status body]}]
  (if (and (= http-status/ok status)
           (map? body)
           (number? (get body "expires_in")))
    (.plusMillis (Instant/now)
                 ;; wait only 90% of the expire time to be safe
                 (long (* (get body "expires_in") 900)))
    (Instant/now)))



;; Local Variables:
;; eval: (put 'clojure.core.cache/defcache 'clojure-indent-function '(2 nil nil (:defn))))
;; End:
