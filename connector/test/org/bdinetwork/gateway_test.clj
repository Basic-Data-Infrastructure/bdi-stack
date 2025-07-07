;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway-test
  (:require [aleph.http :as http]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [manifold.deferred :as d]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.gateway :as sut]
            [org.bdinetwork.gateway.interceptors :as interceptors :refer [interceptor]]
            [org.bdinetwork.gateway.response :as r]
            [org.bdinetwork.test-helper :refer [backend-host backend-port backend-url proxy-host proxy-port proxy-url start-backend start-proxy]]))

(deftest make-gateway
  (testing "minimal"
    (let [gateway
          (sut/make-gateway {:rules [{:match        {:method :test}
                                      :interceptors [(interceptor
                                                      :name  "test"
                                                      :doc   "test"
                                                      :enter (fn [_] {:response 'response}))]}]})]
      (is (= 'response (d/unwrap (gateway {:method :test}))))))

  (testing "no response"
    (let [gateway (sut/make-gateway {:rules [{:match        {}
                                               :interceptors []}]})]
      (is (= r/bad-gateway (d/unwrap (gateway {}))))))

  (testing "no match"
    (let [gateway (sut/make-gateway {:rules [{:match        {:method :test}
                                              :interceptors []}]})]
      (is (= r/not-found (d/unwrap (gateway {:method :dummy}))))))

  (testing "entering and leaving"
    (let [gateway
          (sut/make-gateway
           {:rules [{:match        {}
                     :interceptors [(interceptor
                                     :name  "primi"
                                     :enter (fn [{{:keys [primi]} :request :as ctx}]
                                              (assoc ctx :primi-portion primi))
                                     :leave (fn [{:keys [primi-portion] :as ctx}]
                                              (assoc-in ctx [:response :primi-interceptor-leave] primi-portion)))
                                    (interceptor
                                     :name "secondi"
                                     :enter (fn [{{:keys [secondi]} :request :as ctx}]
                                              (-> ctx
                                                  (assoc :secondi-portion secondi)
                                                  (assoc-in [:response :secondi-interceptor-enter] secondi)))
                                     :leave (fn [{:keys [secondi-portion] :as ctx}]
                                              (assoc-in ctx [:response :secondi-interceptor-leave] secondi-portion)))
                                    (interceptor
                                     :name "unreachable"
                                     :enter (fn [ctx]
                                              (throw (ex-info "unreachable interceptor" ctx))))]}]})]
      (is (= {:primi-interceptor-leave   'a-pinch
              :secondi-interceptor-enter 'a-scoop
              :secondi-interceptor-leave 'a-scoop}
             (d/unwrap (gateway {:primi 'a-pinch, :secondi 'a-scoop}))))))

  (testing "error"
    (testing "catch all interceptor"
      (let [gateway
            (sut/make-gateway
             {:rules [{:match        {}
                       :interceptors [(interceptor
                                       :name  "catch all"
                                       :error (fn [{{:keys [exception]} :error :as ctx}]
                                                (assoc ctx :response {:body (.getMessage exception)})))
                                      (interceptor
                                       :name  "throw"
                                       :enter (fn [_] (throw (Exception. "boom"))))]}]})]
        (is (= {:body "boom"}
               (d/unwrap (gateway {}))))))

    (testing "self catching"
      (let [gateway
            (sut/make-gateway
             {:rules [{:match        {}
                       :interceptors [(interceptor
                                       :name  "throw and catch"
                                       :enter (fn [_] (throw (Exception. "boom")))
                                       :error (fn [{{:keys [exception]} :error :as ctx}]
                                                (assoc ctx :response {:body (.getMessage exception)})))]}]})]
        (is (= {:body "boom"}
               (d/unwrap (gateway {})))))))

  (testing "vars"
    (let [var-keys ['global 'rule1 'rule2 'last-rule]
          gateway
          (sut/make-gateway
           {:vars  {'global "vars"}
            :rules [{:match        {:example "1st"}
                     :vars         {'rule1 "1st"}
                     :interceptors [(interceptor
                                     :name "1st"
                                     :enter (fn [{:keys [vars] :as ctx}] (assoc ctx :response vars)))]}
                    {:match        {:example "2nd"}
                     :vars         {'rule2 "2nd"}
                     :interceptors [(interceptor
                                     :name "2nd"
                                     :enter (fn [{:keys [vars] :as ctx}] (assoc ctx :response vars)))]}
                    {:match        {:example 'last-rule}
                     :interceptors [(interceptor
                                     :name "last"
                                     :enter (fn [{:keys [vars] :as ctx}] (assoc ctx :response vars)))]}]})]
      (is (= {'global "vars"
              'rule1  "1st"}
             (-> (gateway {:example "1st"})
                 (d/unwrap)
                 (select-keys var-keys))))
      (is (= {'global "vars"
              'rule2  "2nd"}
             (-> (gateway {:example "2nd"})
                 (d/unwrap)
                 (select-keys var-keys))))
      (is (= {'global    "vars"
              'last-rule "other"}
             (-> (gateway {:example "other"})
                 (d/unwrap)
                 (select-keys var-keys)))))))



(def rules
  {:vars  {'backend-url backend-url
           'passed      "passed"}
   :rules [{:match        {:uri "/test"}
            :interceptors (mapv #(interceptors/->interceptor % {})
                                [['reverse-proxy/forwarded-headers]
                                 ['request/rewrite 'backend-url]
                                 ['request/update 'update :headers 'assoc "x-gateway-req" 'passed]
                                 ['response/update 'update :headers 'assoc "x-gateway-res" 'passed]
                                 ['reverse-proxy/proxy-request]])}]})

(def proxy-handler (sut/make-gateway rules))

(defn backend-handler [req]
  {:status  http-status/ok
   :headers {"content-type" "application/edn"}
   :body    (-> req
                (select-keys [:request-method :uri :headers :body])
                (update :body slurp)
                (pr-str))})

(use-fixtures :once
  (fn [f]
    (with-resources [_backend (start-backend backend-handler)
                     _proxy   (start-proxy proxy-handler)]
      (f))))

(deftest e2e
  (let [{:keys [status]} @(http/get proxy-url {:throw-exceptions? false})]
    (is (= http-status/not-found status)
        "request not found"))
  (let [{:keys [status headers body]} @(http/get (str proxy-url "/test") {:throw-exceptions? false})]
    (is (= http-status/ok status)
        "request ok")
    (is (= "application/edn" (get headers "content-type"))
        "got edn")
    (is (= "passed" (get headers "x-gateway-res")))

    (testing "what did the backend see"
      (let [{:keys [request-method uri headers]} (-> body (slurp) (edn/read-string))]
        (is (= :get request-method))
        (is (= "/test" uri))
        (is (= {"host" (str backend-host ":" backend-port)
                "content-length" "0"
                "x-forwarded-proto" "http"
                "x-forwarded-host"  (str proxy-host ":" proxy-port)
                "x-forwarded-port"  (str proxy-port)
                "x-gateway-req"     "passed"}
               headers))))))
