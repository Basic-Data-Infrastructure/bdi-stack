;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'org.bdinetwork/ishare-jwt)
(def version (format "0.1.%s" (b/git-count-revs nil)))

(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def class-dir "target/classes")

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     @basis
                :src-dirs  ["src"]
                :pom-data  [[:organization
                             [:name "BDI Network"]
                             [:url "https://bdinetwork.org"]]
                            [:licenses
                             [:license
                              [:name "AGPL-3.0-or-later"]
                              [:url "https://www.gnu.org/licenses/agpl-3.0.en.html"]]]]})
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file  jar-file}))
