;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ring.ishare-validator
  (:require [nl.jomco.openapi.v3.validator.json-schema-validator :as schema-validator]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.walk :as walk]))

(def ishare-openapi-spec
  (io/resource "iSHARE-iSHARE_Scheme_Specification-2.0-swagger.yaml"))

(defn parse-yaml
  [in]
  (with-open [is (io/input-stream in)
              rd (io/reader is)]
    (->> (yaml/parse-stream rd :keywords false)
         ;; yaml parser uses ordered maps and seqs for collections
         ;; convert to normal clojure maps and vectors for quicker
         ;; access and edge cases involving ordered maps
         (walk/postwalk (fn [x]
                          (cond
                            (map? x)
                            (into {} x)
                            (sequential? x)
                            (into [] x)
                            :else
                            x))))))

(def ishare-spec-data
  (parse-yaml ishare-openapi-spec))

(def date-time-pred
  (schema-validator/re-pred #"\A\d\d\d\d-(0\d|1[0-2])-([0-2]\d|3[01])T([01]\d|2[0-3]):\d\d:\d\d(\.\d*)?[Zz]\Z"))

(def context
  (schema-validator/validator-context ishare-spec-data
                                      {:format-predicates
                                       {"date-time" date-time-pred}}))

(defn validate
  [instance schema-path]
  ((schema-validator/schema-validator context schema-path) instance [] schema-path))
