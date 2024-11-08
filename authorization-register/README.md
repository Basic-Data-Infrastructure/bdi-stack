<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Topsector Logistiek
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# BDI Authorization Register

## ⚠ DISCLAIMER ⚠

**The software is for demo purposes only!**  It has not been audited
for security flaws and is not suitable as a starting point to develop
software.  Use at your own risk.

## Context

The BDI is a framework for data sharing between federated
organisations. When developing BDI-compatible components, they are
hard to test in isolation; most components depend on multiple services
which in a production environment are usually hosted at third parties.

Having to coordinate with third parties in order to correctly
configure required services slows down development, testing &
demonstrations. It can also make it difficult to create automated
tests for edge cases (when the test case depends on very specific
responses from services).

BDI Services often rely on an Authorization Register.

## The BDI Authorization Register

This project, the BDI Authorization Register, is a standalone service
that provides delegation evidence based on policies inserted via its
HTTP API.  The AR can be run locally on a developer's hardware, in a
CI environment or -- after security testing -- it can be used as an
Authorization Register for small associations.

This AR implements the iSHARE Authorization Register API, plus an API
for managing authorization policies; delegation evidence is provided
based on the policies present.

Policies are kept in an in-memory database (durability of policies is
work-in-progress), The AR provides a PolicyView protocol for quering
policies and a PolicyStore protocol for adding and deleting policies.

The implemented endpoints are:

- `POST /connect/token` -- [Access token](https://dev.ishare.eu/ishare-satellite-role/access-token-m2m)
- `POST /delegation` -- [Delegation evidence](https://dev.ishare.eu/authorisation-registry-role/delegation-endpoint)
- `POST /policy` -- Insert a new policy based on the given delegation evidence

## Deployment

### Docker image

We publish docker images for every release:

```sh
docker run bdinetwork.azurecr.io/authorization-register
```

### Standalone jar on the JVM

You can download the latest authorization-register zip bundle from the
[github releases
page](https://github.com/Basic-Data-Infrastructure/bdi-stack/releases).

Unzip the jar file and run the application using 

```sh
java -jar bdi-authorization-register.jar
```

## Configuration

The Authorization Register is configured using environment variables and
needs an (initially empty) directory to store its state.

Environment variables:

|Variable                  |Description
|--------------------------|---------------------------------------------------
|`ACCESS_TOKEN_TTL_SECONDS`|Access token time to live in seconds (default: 600)
|`ASSOCIATION_SERVER_ID`   |Association Server id
|`ASSOCIATION_SERVER_URL`  |Assocation Server url
|`HOSTNAME`                |Server hostname
|`POLICIES_DB`             |Directory to store policy data
|`PORT`                    |Server HTTP Port (default: 8080)
|`PRIVATE_KEY`             |Server private key pem file
|`PUBLIC_KEY`              |Server public key pem file
|`SERVER_ID`               |Server ID (EORI)
|`X5C`                     |Server certificate chain pem file

## Development

The ASR is implemented in Clojure, using `tools.deps`. See [the parent
README](../README.md) for info on the required development tooling.

To generate a standalone Java jar, run

```sh
make bdi-authorization-register.jar
```

To build a docker image, **in the parent directory**, run

```sh
docker build -f authorization-register.dockerfile .
```

## Copying

Copyright (C) 2024 Jomco B.V.

Copyright (C) 2024 Topsector Logistiek

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with this program.  If not, see
<http://www.gnu.org/licenses/>.


[AGPL-3.0-or-later](LICENSES/AGPL-3.0-or-later.txt)
