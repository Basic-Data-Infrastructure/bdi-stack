;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.oauth2-test
  (:require [buddy.core.keys :as keys]
            [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [Resource with-resources]]
            [org.bdinetwork.gateway.oauth2 :as sut]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.time Instant)))

(def oauth-private-key (-> "test-config/association_register.key.pem"
                           (io/resource)
                           (keys/private-key)))

(def oauth-public-key (-> "test-config/association_register.x5c.pem"
                          (io/resource)
                          (keys/public-key)))

(def kid "test-kid")

(defn- mk-token [claims & {:keys [typ alg kid private-key]
                           :or   {typ "at+jwt"
                                  alg :rs256
                                  kid kid
                                  private-key oauth-private-key}}]
  (jwt/sign (cond-> claims
              (not (contains? claims :exp))
              (assoc :exp (+ 120 (.getEpochSecond (Instant/now)))))
            private-key
            {:header {:typ typ, :kid kid}
             :alg    alg}))

(def jwks-scheme :http)
(def jwks-host "127.0.0.1")
(def jwks-port 11001)
(def jwks-uri (str (name jwks-scheme) "://" jwks-host ":" jwks-port))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server] (.stop server)))

(defn start-jwks [keys]
  (run-jetty (constantly
              {:status  http-status/ok
               :headers {"content-type" "application/json"}
               :body    (json/write-str {:keys keys})})
             {:host  jwks-host
              :port  jwks-port
              :join? false}))

(def jwks-keys [(-> oauth-public-key
                    (keys/public-key->jwk)
                    (assoc :kid kid))])

(deftest decode-access-token
  (with-resources [_ (start-jwks jwks-keys)]
    (let [opts   {:jwks-uri jwks-uri, :jwks-cache-atom (atom {})}
          claims {:iat   (.getEpochSecond (Instant/now))
                  :iss   "test-issuer"
                  :aud   "test-audience"
                  :sub   "test-subject"
                  :other "other"}
          token  (mk-token claims)]
      (is (= "other"
             (-> (sut/decode-access-token token opts)
                 :other))
          "correctly decoded")

      (is (= "other"
             (-> (sut/decode-access-token token (merge opts claims))
                 :other))
          "correctly decoded and claims verified")

      (is (thrown-with-msg?
           Exception
           #"Not an access token \(JWT\)"
           (-> (mk-token claims {:typ "JWT"})
               (sut/decode-access-token opts))))
      (is (thrown-with-msg?
           Exception
           #"Audience does not match BAD"
           (sut/decode-access-token token (assoc opts :aud "BAD")))
          "bad audience handled by buddy.sign.jwt/unsign")

      (is (thrown-with-msg?
           Exception
           #"Claim other does not match \(other\)"
           (sut/decode-access-token token (assoc opts :other "BAD")))
          "non standard claims handled")

      (is (thrown-with-msg?
           Exception
           #"Unsupported signing algorithm \(rs512\)"
           (-> (mk-token claims {:alg :rs512})
               (sut/decode-access-token opts)))
          "not supported by default")

      (is (thrown-with-msg?
           Exception
           #"Unsupported signing algorithm \(rs256\)"
           (-> (mk-token claims {:alg :rs256})
               (sut/decode-access-token (assoc opts :algs #{:rs512}))))
          "not supported by claims")

      (is (thrown-with-msg?
           Exception
           #"Can not determine maximum token age \(10\)"
           (-> (mk-token (dissoc claims :iat))
               (sut/decode-access-token (assoc opts :max-age 10))))))))
