(ns org.bdinetwork.service-provider.authentication-test
  (:require [buddy.core.keys :as keys]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as status]
            [org.bdinetwork.service-provider.authentication :as auth]
            [org.bdinetwork.service-provider.in-memory-association :refer [in-memory-association read-source]]
            [org.bdinetwork.ishare.jwt :as jwt]))

(deftest jti
  (let [c (auth/mk-jti-cache-atom)]
    (is (auth/new-jti?! c "one"))
    (is (not (auth/new-jti?! c "one")))
    (is (auth/new-jti?! c "two"))
    (is (not (auth/new-jti?! c "two")))
    (is (not (auth/new-jti?! c "one")))))

(def client-id "EU.EORI.CLIENT")
(def server-id "EU.EORI.SERVER")

(def association
  (in-memory-association (read-source "test/test-config.yml")))

(def server-private-key
  (keys/private-key "test/pem/server.key.pem"))

(def server-public-key
  (keys/public-key "test/pem/server.cert.pem"))

(def client-private-key
  (keys/private-key "test/pem/client.key.pem"))

(defn pem->x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))

(def client-x5c
  (pem->x5c "test/pem/client.x5c.pem"))

(def handler
  (-> (fn [{:keys [client-id] :as req}]
        (if client-id
          {:status status/ok
           :body   "Authenticated"
           :req    req}
          {:status status/forbidden
           :body   "Not authenticated"
           :req    req}))
      (auth/wrap-authentication {:private-key              server-private-key
                                 :public-key               server-public-key
                                 :server-id                server-id
                                 :access-token-ttl-seconds 600})))

(defn mk-client-assertion
  []
  (jwt/make-client-assertion #:ishare {:client-id client-id
                                       :server-id server-id
                                       :private-key client-private-key
                                       :x5c client-x5c}))

(defn mk-auth-request
  []
  {:request-method :post

   :uri         "/connect/token"
   :params      {"client_assertion"      (mk-client-assertion)
                 "client_id"             client-id
                 "grant_type"            "client_credentials"
                 "client_assertion_type" "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                 "scope"                 "iSHARE"}
   :association association})

(defn token-request
  [req token]
  (assoc-in req [:headers "authorization"] (str "Bearer " token)))

(deftest incorrect-auth
  (doseq [[res req] [[{:status 405}
                      {:uri "/connect/token"}]
                     ;; TODO add more examples here
                     ]]
    (is (= res (handler req)))))

(deftest correct-auth
  (let [res (handler {:request-method :get
                      :uri "/service"})]
    (is (= status/forbidden (:status res))
        "Unauthenticated request"))

  (let [res (handler (mk-auth-request))
        token (get-in res [:body :access_token])]
    (is (= status/ok (:status res)))
    (is (some? token))

    (let [res (handler (-> {:request-method :get
                            :uri "/service"}
                           (token-request token)))]
      (is (= status/ok (:status res))
          "Authenticated request")
      (is (= client-id (:client-id (:req res)))))))
