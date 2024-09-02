(ns org.bdinetwork.authorization-register.web
  (:require [compojure.core :refer [GET defroutes]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]
            [org.bdinetwork.service-provider.authentication :as authentication]
            [org.bdinetwork.ishare.jwt :as ishare.jwt]))
