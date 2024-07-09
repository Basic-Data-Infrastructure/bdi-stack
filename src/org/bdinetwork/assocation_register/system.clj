(ns org.bdinetwork.assocation-register.system
  (:require [org.bdinetwork.assocation-register.data-source :as ds]
            [org.bdinetwork.assocation-register.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]
            [buddy.core.keys :refer [private-key]]
            [clojure.string :as string]))

(defn x5c
  "Read chain file into vector of certificates."
  [cert-file]
  (->> (-> cert-file
           slurp
           (string/replace-first #"(?s)\A.*?-+BEGIN CERTIFICATE-+\s+" "")
           (string/replace #"(?s)\s*-+END CERTIFICATE-+\s*\Z" "")
           (string/split #"(?s)\s*-+END CERTIFICATE-+.*?-+BEGIN CERTIFICATE-+\s*"))
       (mapv #(string/replace % #"\s+" ""))))

(defn run-system
  []
  (let [handler (web/make-handler (ds/read-data-source "test/example-config.yml")
                                  {:server-id "EU.EORI.SERVER"
                                   :x5c (x5c "test/pem/server.x5c.pem")
                                   :private-key (private-key "test/pem/server.key.pem")})]
    (run-jetty handler {:join? true :port 8080 :hostname "localhost"})))
