;;; SPDX-FileCopyrightText: 2024 Jomco B.V.
;;; SPDX-FileCopyrightText: 2024 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.service-provider.association)

(defprotocol Association
  "Provides info on registered parties and root CAs in an association"
  (party [this party-id]
    "Return the registered party with `party-id` or nil if no such
    party is registered.")
  (trusted-list [this]
    "Return the fingerprints of the trusted root CA certificates."))
