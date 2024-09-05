(ns org.bdinetwork.authorization-register.system
  (:require [nl.jomco.resources :refer [mk-system Resource close]]
            [org.bdinetwork.authorization-register.in-memory-policies :refer [in-memory-policies]]
            [org.bdinetwork.authorization-register.web :as web]
            [org.bdinetwork.service-provider.remote-association :refer [remote-association]]
            [ring.adapter.jetty :refer [run-jetty]]
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

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [jetty]
    (.stop jetty)))

(defn run-system
  [{:keys [client-id x5c private-key association-server-id association-server-url] :as config}]
  (mk-system [policies    (in-memory-policies)
              association (remote-association #:ishare {:client-id          client-id
                                                        :x5c                x5c
                                                        :private-key        private-key
                                                        :satellite-id       association-server-id
                                                        :satellite-endpoint association-server-url})
              app         (web/mk-app {:policy-store policies
                                       :policy-view  policies
                                       :association  association}
                                      config)
              jetty       (run-jetty app config)]
    {:policies    policies
     :app         app
     :association association
     :jetty       jetty}))
