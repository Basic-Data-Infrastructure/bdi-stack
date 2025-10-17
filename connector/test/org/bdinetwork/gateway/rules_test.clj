;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.rules-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [org.bdinetwork.gateway.interceptors :refer [->interceptor interceptor]]
            [org.bdinetwork.gateway.rules :as sut]))

(defmethod ->interceptor 'dummy-with-args
  [[id] args]
  (interceptor
   :name (str id " " args)
   :doc "Do nothing just take args"
   :enter identity
   :leave identity))

(deftest read-rules-file
  (let [rules (sut/read-rules-file (io/resource "test-rules.edn") :args)]
    (is (= [{:headers {"authorization" "Basic Zm9vOmJhcg=="}}
            {}]
           (->> rules :rules (map :match)))
        "aero magic applied")
    (is (= [["reverse-proxy/forwarded-headers"
             "dummy-with-args :args"
             "request/update (assoc :scheme :http :server-name \"localhost\" :server-port 1234)"
             "request/update (update :headers assoc \"authorization\" \"Basic em9vOnF1dXg=\")"
             "response/update (update :headers assoc \"x-bdi-connector\" \"passed\")"
             "reverse-proxy/proxy-request"]
            ["respond {:status 401, :headers {\"content-type\" \"text/plain\", \"www-authenticate\" \"Basic realm=\\\"secret\\\"\"}, :body \"not allowed\"}"]]
           (->> rules :rules (map :interceptors) (map #(map :name %))))
        "interceptors interpreted")))
