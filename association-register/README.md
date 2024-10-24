<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Topsector Logistiek
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# BDI Association Registry

## ⚠ DISCLAIMER ⚠

**The software is for demo purposes only!**  It has not been audited
for security flaws and is not suitable as a starting point to develop
software.  Use at your own risk.

## In development

NOTE: This repository is work in progress. This document sets out the
goals of the projects, not the current state of affairs.

## Context

The BDI is a framework for data sharing between federated
organisations. When developing BDI-compatible components, they are
hard to test in isolation; most components depend on multiple services
which in a production environment are usually hosted at multiple third
parties.

Having to coordinate with third parties in order to correctly
configure required services slows down development, testing &
demonstrations. It can also make it difficult to create automated
tests for edge cases (when the test case depends on very specific
responses from services).

All BDI components depend, directly or indirectly, on an Association
Register, that provides information about registered parties, their
roles and their credentials (certificates holding public keys).

## What (scope)

This project, the BDI Assocation Register (ASR), implements the
complete public Assocation Register API and can be run locally on a
developer's hardware, in a CI environent or can be used as an
Assocation Register for small associations.

The ASR API is described as the "iSHARE Satellite Role" at [the iSHARE
developer documentation](https://dev.ishare.eu/common/token.html).

The ASR can be run as a standalone Java jar, or as a docker container
and can be statically configured using a configuration file. The ASR
does not provide a user interface and has no management API.

## Deployment

### Standalone jar on the JVM

### Docker image

## Development

The ASR is implemented in Clojure, using `tools.deps`.

## Configuration

The ASR is configured using a single YAML file. Party certificates
must be provided as X509 pem files containing the full certificate
chain for a party.

```yaml
service:
    # local port binding
    port: 8080
    # network binding
    hostname: localhost
parties:
    # for every party, the data as described in the
    # `parties_info` object in
    # https://dev.ishare.eu/satellite/parties.html#response
    - party_id:  EU.EORI.FLEXTRANS
      party_name: Flex Transport BV
      capability_url: "https://..."
      registrar_id: ...
```


## Copying

Copyright (C) 2024 Jomco B.V.

Copyright (C) 2024 Topsector Logistiek

[AGPL-3.0-or-later](LICENSES/AGPL-3.0-or-later.txt)
