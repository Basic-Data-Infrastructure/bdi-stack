;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.gateway-test
  (:require [clojure.test :refer [deftest is testing]]
            [org.bdinetwork.connector.gateway :as sut]
            [org.bdinetwork.connector.response :as response]))

(deftest make-gateway
  (testing "minimal"
    (let [gateway
          (sut/make-gateway {:rules [{:match        {:method :test}
                                      :interceptors [{:enter (fn [_] {:response 'response})}]}]})]
      (is (= 'response (gateway {:method :test})))))

  (testing "no response"
    (let [gateway
          (sut/make-gateway {:rules [{:match        {}
                                      :interceptors []}]})]
      (is (= response/bad-gateway (gateway {})))))

  (testing "no match"
    (let [gateway
          (sut/make-gateway {:rules [{:match        {:method :test}
                                      :interceptors []}]})]
      (is (= response/not-found (gateway {:method :dummy})))))

  (testing "entering and leaving"
    (let [gateway
          (sut/make-gateway
           {:rules [{:match        {}
                     :interceptors [{:enter (fn [{{:keys [primi]} :request :as ctx}]
                                              (assoc ctx :primi-portion primi))
                                     :leave (fn [{:keys [primi-portion] :as ctx}]
                                              (assoc-in ctx [:response :primi-interceptor-leave] primi-portion))}
                                    {:enter (fn [{{:keys [secondi]} :request :as ctx}]
                                              (-> ctx
                                                  (assoc :secondi-portion secondi)
                                                  (assoc-in [:response :secondi-interceptor-enter] secondi)))
                                     :leave (fn [{:keys [secondi-portion] :as ctx}]
                                              (assoc-in ctx [:response :secondi-interceptor-leave] secondi-portion))}
                                    {:enter (fn [ctx]
                                              (throw (ex-info "unreachable interceptor" ctx)))}]}]})]
      (is (= {:primi-interceptor-leave   'a-pinch
              :secondi-interceptor-enter 'a-scoop
              :secondi-interceptor-leave 'a-scoop}
             (gateway {:primi 'a-pinch, :secondi 'a-scoop})))))

  (testing "vars"
    (let [var-keys ['global 'rule1 'rule2 'last-rule]
          gateway
          (sut/make-gateway
           {:vars  {'global "vars"}
            :rules [{:match        {:example "1st"}
                     :vars         {'rule1 "1st"}
                     :interceptors [{:enter (fn [{:keys [eval-env] :as ctx}] (assoc ctx :response eval-env))}]}
                    {:match        {:example "2st"}
                     :vars         {'rule2 "2st"}
                     :interceptors [{:enter (fn [{:keys [eval-env] :as ctx}] (assoc ctx :response eval-env))}]}
                    {:match        {:example 'last-rule}
                     :interceptors [{:enter (fn [{:keys [eval-env] :as ctx}] (assoc ctx :response eval-env))}]}]})]
      (is (= {'global "vars"
              'rule1  "1st"}
             (-> (gateway {:example "1st"})
                 (select-keys var-keys))))
      (is (= {'global "vars"
              'rule2  "2st"}
             (-> (gateway {:example "2st"})
                 (select-keys var-keys))))
      (is (= {'global    "vars"
              'last-rule "other"}
             (-> (gateway {:example "other"})
                 (select-keys var-keys)))))))
