;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.cache
  (:require [clojure.core.cache :as cache]
            [nl.jomco.http-status-codes :as http-status])
  (:import java.time.Instant))

(defn- prune-cache [cache]
  (let [now (Instant/now)]
    (->> cache
         (filter #(.isBefore now (-> % val :expires)))
         (into {}))))

(cache/defcache ExpiresCache [cache f]
  cache/CacheProtocol
  (lookup [_ item]
    (get-in cache [item :result]))
  (lookup [_ item not-found]
    (get-in cache [item :result] not-found))
  (has? [_ item]
    (-> cache
        (prune-cache)
        (contains? item)))
  (hit [_ _item]
    (ExpiresCache. (prune-cache cache) f))
  (miss [_ item result]
    (ExpiresCache. (-> cache
                       (prune-cache)
                       (assoc item {:result  result
                                    :expires (f result)}))
                   f))
  (evict [_ item]
    (ExpiresCache. (-> cache
                       (prune-cache)
                       (dissoc item))
                   f))
  (seed [_ base]
    (ExpiresCache. base f))

  Object
  (toString [_] (str cache)))

(defn expires-cache-factory
  "Create a cache object with `f` to determine when the cached result expires."
  [f]
  (ExpiresCache. {} f))

(defn bearer-token-expires-at
  "Derive from response when bearer token expires."
  [{:keys [status body]}]
  (if (and (= http-status/ok status)
           (map? body)
           (int? (get body "expires_in")))
    (.plusSeconds (Instant/now) (* (get body "expires_in") 0.8))
    (Instant/now)))

(defn get-through-cache-atom
  [cache-atom f args]
  (get (swap! cache-atom cache/through-cache args f) args))



;; Local Variables:
;; eval: (put 'clojure.core.cache/defcache 'clojure-indent-function '(2 nil nil (:defn))))
;; End:
