<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Topsector Logistiek
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# BDI Authentication Service

## ⚠ DISCLAIMER ⚠

**The software is for demo purposes only!**  It has not been audited
for security flaws and is not suitable as a starting point to develop
software.  Use at your own risk.

## Context

The BDI is a framework for data sharing between federated
organisations. Clients can authenticate themselves with a service by
providing a self-signed client assertion. The client assertion must be
verified using information from the federation's [Association
Register](../association-register/README.md).

## The BDI Authentication Service

This project, the BDI Authentication Service, is a standalone service
that provides an access token endpoint and generates signed access
tokens (JWTs) when the client is successfully authenticated as a
trusted member of the Association Register. 

Services can rely on a trusted instance of the Authentication Service,
meaning they only have to verify that incoming requests contain a
valid access token signed by the Authentication Service, and if so,
the information in the access token can be trusted.

## API

This service implements the iSHARE Authentication API; a single
endpoint:

- `POST /connect/token` -- [Access token](https://dev.ishare.eu/ishare-satellite-role/access-token-m2m)

## Deployment

### Docker image

We publish docker images for every release:

```sh
docker run bdinetwork.azurecr.io/authentication-service
```

### Standalone jar on the JVM

You can download the latest authorization-register zip bundle from the
[github releases
page](https://github.com/Basic-Data-Infrastructure/bdi-stack/releases).

Unzip the jar file and run the application using 

```sh
java -jar bdi-authentication-service.jar
```

## Configuration

The Authentication Service is configured using environment variables.

Environment variables:

|Variable                  |Description
|--------------------------|---------------------------------------------------
|`ACCESS_TOKEN_TTL_SECONDS`|Access token time to live in seconds (default: 600)
|`ASSOCIATION_SERVER_ID`   |Association Server id
|`ASSOCIATION_SERVER_URL`  |Assocation Server url
|`HOSTNAME`                |Server hostname
|`PORT`                    |Server HTTP Port (default: 8080)
|`PRIVATE_KEY`             |Server private key pem file
|`PUBLIC_KEY`              |Server public key pem file
|`SERVER_ID`               |Server ID (EORI) will be used as the `iss` and `aud` claims of the access token JWT
|`X5C`                     |Server certificate chain pem file

## Development

The service is implemented in Clojure, using `tools.deps`. See [the parent
README](../README.md) for info on the required development tooling.

To generate a standalone Java jar, run

```sh
make bdi-authentication-service.jar
```

To build a docker image, **in the parent directory**, run

```sh
docker build -f authentication-service.dockerfile .
```

## Copying

Copyright (C) 2025 Jomco B.V.

Copyright (C) 2025 Topsector Logistiek

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
