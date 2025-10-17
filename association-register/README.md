<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Stichting Connekt
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# BDI Association Register

## ⚠ DISCLAIMER ⚠

**The software is for development purposes only!**  It has not been
audited for security flaws. Use at your own risk.

## Context

The BDI is a framework for data sharing between federated
organizations. When developing BDI-compatible components, they are
hard to test in isolation; most components depend on multiple services
which in a production environment are usually hosted at third parties.

Having to coordinate with third parties in order to correctly
configure required services slows down development, testing &
demonstrations. It can also make it difficult to create automated
tests for edge cases (when the test case depends on very specific
responses from services).

All BDI components depend, directly or indirectly, on an Association
Register which provides information about registered parties, their
roles and their credentials (certificates holding public keys). This
information about parties is used during
[authentication](https://dev.ishare.eu/reference/authentication)
to ensure that parties are registered with the given certificate. The
party info can also be used as a basis for authorizing parties given
their signed agreements, level of assurance and other attributes.

## The BDI Association Register

This project, the BDI Association Register (ASR), implements a subset
of the public [iSHARE Satellite
API](https://dev.ishare.eu/ishare-satellite-role/single-party) and can
be run locally on a developer's hardware, in a CI environment or --
after security testing -- it can be used as an Association Register for
small associations.

The implemented endpoints are:

- `POST /connect/token` -- Access Token
- `GET /parties/{party_id}` -- Single party info
- `GET /trusted_list` -- Trusted certificate authorities

The ASR API is described as the "iSHARE Satellite Role" at [the iSHARE
developer documentation](https://dev.ishare.eu/common/token.html).

The ASR can be run as a standalone Java jar, or as a docker container
and can be statically configured using a configuration file. The ASR
does not provide a user interface and has no management API.

## Deployment

### Docker image

We publish docker images for every release:

```sh
docker run bdinetwork.azurecr.io/association-register
```

### Standalone jar on the JVM

You can download the latest association-register zip bundle from the
[github releases
page](https://github.com/Basic-Data-Infrastructure/bdi-stack/releases).

Unzip the jar file and run the application using 

```sh
java -jar bdi-association-register.jar
```

## Configuration

The Associaton Register is configured using environment variables and
a YAML configuration file.

Environment variables:

|Variable                  |Description
|--------------------------|--------------------------------------------------
|`ACCESS_TOKEN_TTL_SECONDS`|Access token time to live in seconds (default 600)
|`DATA_SOURCE`             |YAML file specifying parties and trusted list
|`HOSTNAME`                |Server hostname
|`PORT`                    |Server HTTP Port (default 9902)
|`PRIVATE_KEY`             |Server private key pem file
|`PUBLIC_KEY`              |Server public key pem file
|`SERVER_ID`               |Server ID (EORI)
|`X5C`                     |Server certificate chain pem file

The data source YAML file contains party information and trusted
list. Certificates can be provided as X509 pem files.

```yaml
trusted_list:
  - path/to/ca.cert.pem
  - path/to/other.cert.pem
parties:
  # for every party, the data as described in the
  # `parties_info` object in
  # https://dev.ishare.eu/satellite/parties.html#response
  #
  - party_id:  EU.EORI.FLEXTRANS
    party_name: Flex Transport BV
    capability_url: "https://..."
    registrar_id: ...
    certificates:
       # certificate data can be provided as a path to a PEM file
       - path/to/flextrans.cert.pem
```

## Development

The ASR is implemented in Clojure, using `tools.deps`. See [the parent
README](../README.md) for info on the required development tooling.

To generate a standalone Java jar, run

```sh
make bdi-association-register.jar
```

To build a docker image, **in the parent directory**, run

```sh
docker build -f association-register.dockerfile .
```

## Copying

Copyright (C) 2024 Jomco B.V.

Copyright (C) 2024 Stichting Connekt

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
