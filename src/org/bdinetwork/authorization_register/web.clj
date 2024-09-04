(ns org.bdinetwork.authorization-register.web
  (:require [compojure.core :refer [GET defroutes]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]
            [nl.jomco.http-status-codes :as status]
            [org.bdinetwork.service-provider.authentication :as authentication]
            [org.bdinetwork.authorization-register.policy :as policy]
            [org.bdinetwork.ishare.jwt :as ishare.jwt]))

(defn wrap-token-response
  [handler {:keys [private-key x5c server-id]}]
  (fn [{:keys [client-id] :as request}]
    (let [{:keys [body token-key] :as response} (handler request)]
      (if (and client-id token-key)
        (assoc response :body {token-key (ishare.jwt/make-jwt (assoc body
                                                                     :iss server-id
                                                                     :sub server-id ;; TODO: check this
                                                                     :aud client-id)
                                                              private-key
                                                              x5c)})
        response))))

;; https://dev.ishare.eu/reference/delegation-mask


(defroutes routes
  (GET "/delegation"
      {:keys                     [client-id
                                  policy-view]
       {:strs [delegationRequest]} :body}
    (if (not client-id)
        {:status status/unauthorized}
        {:status status/ok
         :body (policy/delegation-evidence policy-view delegationRequest)
         :token-key :delegation_token})))
