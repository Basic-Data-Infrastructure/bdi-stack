;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.rules-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [org.bdinetwork.connector.rules :as sut]))

(deftest read-rules-file
  (let [rules (sut/read-rules-file (io/resource "test-rules.edn"))]
    (is (= [{:headers {"authorization" "Basic Zm9vOmJhcg=="}}
            {}]
           (->> rules :rules (map :match)))
        "aero magic applied")
    (is (= [["reverse-proxy/forwarded-headers"
             "request/update (assoc :scheme :http :server-name \"localhost\" :server-port 1234)"
             "request/update (update :headers assoc \"authorization\" \"Basic em9vOnF1dXg=\")"
             "response/update (update :headers assoc \"x-bdi-connector\" \"passed\")"
             "reverse-proxy/proxy-request"]
            ["respond {:status 401, :headers {\"content-type\" \"text/plain\", \"www-authenticate\" \"Basic realm=\\\"secret\\\"\"}, :body \"not allowed\"}"]]
           (->> rules :rules (map :interceptors) (map #(map :name %))))
        "interceptors interpreted")))
