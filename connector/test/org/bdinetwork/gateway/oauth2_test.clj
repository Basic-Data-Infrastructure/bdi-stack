;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.oauth2-test
  (:require [clojure.test :refer [deftest is testing]]
            [nl.jomco.resources :refer [with-resources]]
            [org.bdinetwork.gateway.oauth2 :as sut]
            [org.bdinetwork.test-helper :refer [start-openid openid-uri mk-token jwks-keys jwks-uri]])
  (:import (java.time Instant)))

(deftest unsign-access-token
  (with-resources [_ (start-openid jwks-keys)]
    (let [opts   {:jwks-cache-atom (atom {})}
          claims {:iat   (.getEpochSecond (Instant/now))
                  :iss   openid-uri
                  :aud   "test-audience"
                  :sub   "test-subject"
                  :other "other"}
          token  (mk-token claims)]
      (is (= "other"
             (-> @(sut/unsign-access-token token opts)
                 :other))
          "correctly decoded")

      (is (= "other"
             (-> @(sut/unsign-access-token token (merge opts claims))
                 :other))
          "correctly decoded and claims verified")

      (is (thrown-with-msg?
           Exception
           #"Not an access token \(JWT\)"
           (-> (mk-token claims {:typ "JWT"})
               (sut/unsign-access-token opts)
               deref)))
      (is (thrown-with-msg?
           Exception
           #"Audience does not match BAD"
           @(sut/unsign-access-token token (assoc opts :aud "BAD")))
          "bad audience handled by buddy.sign.jwt/unsign")
      (is (thrown-with-msg?
           Exception
           #"Issuer does not match \(.*\)"
           @(sut/unsign-access-token token (assoc opts :iss "BAD"))))

      (is (thrown-with-msg?
           Exception
           #"Claim other does not match \(other\)"
           @(sut/unsign-access-token token (assoc opts :other "BAD")))
          "non standard claims handled")

      (is (thrown-with-msg?
           Exception
           #"Unsupported signing algorithm \(rs512\)"
           (-> (mk-token claims {:alg :rs512})
               (sut/unsign-access-token opts)))
          "not supported by default")

      (is (thrown-with-msg?
           Exception
           #"Unsupported signing algorithm \(rs256\)"
           (-> (mk-token claims {:alg :rs256})
               (sut/unsign-access-token (assoc opts :algs #{:rs512}))))
          "not supported by claims")

      (is (thrown-with-msg?
           Exception
           #"Can not determine maximum token age \(10\)"
           (-> (mk-token (dissoc claims :iat))
               (sut/unsign-access-token (assoc opts :max-age 10))
               deref)))

      (testing "with custom jwks-uri"
        (is (= "other"
               (-> (mk-token (assoc claims :iss "dummy"))
                   (sut/unsign-access-token (assoc opts :jwks-uri jwks-uri))
                   deref
                   :other))
            "correctly decoded")))))
