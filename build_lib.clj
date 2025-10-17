;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns build-lib
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]
            [clojure.string :as string]
            [clojure.tools.deps.util.io :as io]))

(defn jar-file
  [lib]
  (format "%s.jar" (name lib)))

(defn class-dir
  [lib]
  (str (name lib) "/target/classes"))

(defn versioned-edn
  "Read a deps.edn file, replacing all :local/root deps with
  a :mvn/version of `version`"
  [lib version]
  (-> (str (name lib) "/" "deps.edn")
      (io/slurp-edn)
      (update :deps update-vals #(if (:local/root %) {:mvn/version version} %))))

(defn basis
  [lib version]
  (b/create-basis {:project (versioned-edn lib version)}))

(defn scm
  [lib version]
  {:tag (str "v" version)
   :url "https://github.com/Basic-Data-Infrastructure/bdi-stack"
   :dir lib})

(defn jar
  [{:keys [lib version deploy?]}]
  (let [version (string/replace (name version) #"^v" "")]
    (b/write-pom {:class-dir (class-dir lib)
                  :lib       lib
                  :version   version
                  :basis     (basis lib version)
                  :scm       (scm (name lib) version)
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
      (dd/deploy {:installer     :remote
                  :pom-file      (str (class-dir lib)
                                      "/META-INF/maven/"
                                      (str lib)
                                      "/pom.xml")
                  :artifact      (jar-file lib)
                  :sign-release? false}))))
