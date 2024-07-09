(ns org.bdinetwork.assocation-register.web
  (:require [compojure.core :refer [GET POST defroutes]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]
            [org.bdinetwork.assocation-register.data-source :as ds]))

(defroutes routes
  (GET "/parties" {:keys [params data-source]}
    {:body {:parties (ds/parties data-source params)}})
  (constantly
   (not-found "Resource not found")))

(defn wrap-datasource
  [handler ds]
  (fn [request]
    (handler (assoc request :data-source ds))))

(defn make-handler
  [data-source]
  (-> routes
      (wrap-datasource data-source)
      (wrap-params)
      (wrap-json-response)))
