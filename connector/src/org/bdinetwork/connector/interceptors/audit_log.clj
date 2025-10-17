;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.connector.interceptors.audit-log
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [hiccup2.core :as hiccup]
            [nl.jomco.http-status-codes :as http-status])
  (:import (java.text SimpleDateFormat)
           (java.util Date)))

(defn- has-mdc? [{:strs [mdc]}] (seq mdc))

(defn- read-lines
  [{:keys [json-file n-of-lines] :or {n-of-lines 100}}]
  (with-open [in (-> json-file io/file io/reader)]
    (binding [*in* in]
      (loop [b (list)]
        (if-let [s (read-line)]
          (let [d (json/read-str s)]
            (recur (if (has-mdc? d)
                     (take n-of-lines (cons d b))
                     b)))
          b)))))

(defn- to-date-time [timestamp]
  (.format (SimpleDateFormat. "yyyy/MM/dd HH:mm:ss")
           (Date. timestamp)))

(def audit-log-css "
table{background:#fff;border-collapse: collapse;}
th,td{padding:.5em;border:solid black 1px;}
tr:nth-child(even){background:#eee}
")

(defn audit-log-response [opts]
  (let [lines (read-lines opts)
        cols  (into #{} (mapcat #(-> % (get "mdc") keys) lines))]
    {:status  http-status/ok
     :headers {"content-type" "text/html"}
     :body    (-> [:html
                    [:head [:style audit-log-css]]
                    [:body
                     (if (seq lines)
                       [:table
                        [:tr
                         [:th "timestamp"]
                         (for [col cols] [:th col])]
                        (for [line lines]
                          [:tr
                           [:td (to-date-time (get line "timestamp"))]
                           (for [col cols]
                             [:td (get-in line ["mdc" col])])])]
                       [:em "log empty"])]]
                  hiccup/html
                  str)}))
