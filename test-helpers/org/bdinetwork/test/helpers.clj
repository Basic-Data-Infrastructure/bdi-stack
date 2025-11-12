;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.test.helpers
  (:import
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)))

(defn temp-dir
  "Create a temporary directory that will be deleted when the JVM exits."
  []
  (let [dir (Files/createTempDirectory "bdi-stack-test" (make-array FileAttribute 0))]
    (.deleteOnExit (.toFile dir))
    (str dir)))
