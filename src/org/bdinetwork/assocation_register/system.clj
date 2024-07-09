(ns org.bdinetwork.assocation-register.system
  (:require [org.bdinetwork.assocation-register.data-source :as ds]
            [org.bdinetwork.assocation-register.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn run-system
  [{:keys [port hostname] :as _config}]
  {:pre [port hostname]}
  (let [handler (web/make-handler (ds/read-data-source "test/example-config.yml"))]
    (run-jetty handler {:join? true :port port :hostname hostname})))
