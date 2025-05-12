<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Topsector Logistiek
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# BDI Ring Middleware

Clojure/Ring middleware for implementing the machine-to-machine
authentication mechanisms of a BDI service provider.

Also provides an `Association` clojure protocol as the basic data
source for assocation information, with an implementation for
in-memory data, and an implementation that fetches the association
information from a remote Association Register.

This is a work in progress and might be split up at a later date. The
immediate goal for this project is to provide a shared basis for
implementing an Assocation Register and an Authorization Register.

## Coordinates

[![Clojars Project](https://img.shields.io/clojars/v/org.bdinetwork/clj-ring-middleware.svg)](https://clojars.org/org.bdinetwork/clj-ring-middleware)

## ⚠ DISCLAIMER ⚠

**The software is for demo purposes only!**  It has not been audited
for security flaws and is not suitable as a starting point to develop
software.  Use at your own risk.

## Usage

For full authentication (a means to get an access token and have it
validate when provided), the following adds a `/connect/token`
endpoint to a ring handler for clients to obtain an access token and
sets `client_id` on the request when a valid access token is provided
by the caller.

```clojure
(require '(org.bdinetwork.ring [authentication :refer [wrap-authentication]]
                               [remote-association :refer [remote-association
                                                           wrap-association]]))
(require '(org.bdinetwork.ishare [jwt :refer [x5c->first-public-key]]))

(defn wrap-connect-token-and-validate-access-token
  [handler
   {:ishare/keys [client-id private-key x5c] :as client-data}]
  (-> handler
      (wrap-authentication {:server-id client-id
                            :public-key (x5c->first-public-key x5c)
                            :private-key private-key
                            :access-token-ttl-seconds 10})
      (wrap-association (remote-association client-data)))))
```

Note that the above does not enforce authentication.  To enforce
authentication the handler should respond to the absence of a
`client-id` itself with some kind unauthorized response.

The resulting handler depends on
 `ring.middleware.json/wrap-json-response` and
 `ring.middleware.params/wrap-params` middleware to be applied on the
 final handler.

- `ishare/client-id`

  The client ID of the party implementing this service.

- `ishare/x5c`

  The certificate chain (starting with the public key) of this service.

- `ishare/private-key`

  The private key of this service for signing access tokens.

- `ishare/satellite-id`

  The client ID of the association register to consult.

- `ishare/satellite-base-url`

  The URL to the association register.

## Copying

Copyright (C) 2024, 2025 Jomco B.V.

Copyright (C) 2024, 2025 Topsector Logistiek

[AGPL-3.0-or-later](LICENSES/AGPL-3.0-or-later.txt)
