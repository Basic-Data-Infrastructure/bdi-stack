;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns build-lib
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(defn jar-file
  [lib]
  (format "%s.jar" (name lib)))

(defn class-dir
  [lib]
  (str (name lib) "/target/classes"))

(defn basis
  [lib]
  (b/create-basis {:project (str (name lib) "/" "deps.edn")}))

(defn jar
  [{:keys [lib version deploy?]}]
  (b/write-pom {:class-dir (class-dir lib)
                :lib       lib
                :version   (name version)
                :basis     (basis lib)
                :pom-data  [[:organization
                             [:name "BDI Network"]
                             [:url "https://bdinetwork.org"]]
                            [:licenses
                             [:license
                              [:name "AGPL-3.0-or-later"]
                              [:url "https://www.gnu.org/licenses/agpl-3.0.en.html"]]]]})
  (b/copy-dir {:src-dirs   [(str (name lib) "/src") (str (name lib) "/resources")]
               :target-dir (class-dir lib)})
  (b/jar {:class-dir (class-dir lib)
          :jar-file  (jar-file lib)})
  (when deploy?
    (dd/deploy {:installer :remote
                :pom-file       (str (class-dir lib)
                                     "/META-INF/maven/"
                                     (str lib)
                                     "/pom.xml")
                :artifact (jar-file lib)
                :sign-release? false})))
