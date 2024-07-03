(ns org.bdinetwork.assocation-register.data-source
  (:require [org.bdinetwork.assocation-register.ishare-validator :refer [parse-yaml validate]]))

(defn read-data-source
  [in]
  (let [source (parse-yaml in)]
    (when-let [issues (validate (get source "parties")
                                ["components" "schemas" "PartiesInfo" "properties" "data"])]
      (throw (ex-info "Invalid party in data source" {:issues issues})))
    source))

(defn parties
  [source params]
  (let [validator ()])
  )
