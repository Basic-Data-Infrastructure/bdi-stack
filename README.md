<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Stichting Connekt
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# BDI Stack

Tools for developing clients and services in the [Basic Data
Infrastructure](https://bdinetwork.org/) framework.

The BDI Stack contains standalone services and libraries.

## Services

- [authentication-service](./authentication-service) - A standalone
  iSHARE / BDI compatible authentication service providing trusted
  access tokens.
- [association-register](./association-register) - An iSHARE / BDI
  compatible Association Register
- [authorization-register](./authorization-register) - An iSHARE / BDI
  compatible Authorization Register
- [connector](./connector) - a standalone service for authenticating
  and authorizing incoming HTTP requests.

## Libraries

- [clj-ishare-jwt](./clj-ishare-jwt) - A Clojure Library for creating
  and validating iSHARE compatible JWTs
- [clj-ishare-client](./clj-ishare-client) - A Clojure Library for
  accessing iSHARE compatible services
- [clj-ring-middleware](./clj-ring-middleware) -
  Ring middleware for authenticating iSHARE compatible clients

## ⚠ DISCLAIMER ⚠

**The software is for development and testing purposes only!**  It has
not been audited for security flaws and may not be suitable as a
starting point for production quality software.  Use at your own risk.

## Running services in docker

We publish services as docker images.

The VGU Demo repository contains a [docker
example](https://github.com/Basic-Data-Infrastructure/demo-vertrouwde-goederenafgifte/tree/master/docker-example)
that demonstrates running an application with the Authorization
Register and Association Register using docker-compose.

## Developing

### Development tooling

You need [Clojure
installed](https://clojure.org/guides/install_clojure) in order to
work with the source code in this repository. You also need GNU Make
and OpenSSL installed.

See the [Makefile](./Makefile) for some common tasks.

### Repository layout

This repository has each component in its own subdirectory as a
Clojure `deps.edn` project. The top-level directory contains a
`deps.edn` configuration that includes all components. 

If a Clojure REPL is started from one of the component directories, it
will include the source paths of the component and its
dependencies. 

If a REPL is started in the top-level directory, it will include the
source paths of all components. This is recommended if you're making
changes across components. To start a top-level REPL in Emacs/CIDER,
open [the top-level deps.edn](./deps.edn) in Emacs and `cider-jack-in`
from there.

### Running tests

Running tests only works from the top-level project, since the tests
rely on a top-level configuraton directory `test-config`. Before
running the tests, run `make test-config` to generate the necessary
key can certificate files.

Some tests need a PostgreSQL database to run.  In the example below a
docker container is started to service as test database:

```sh
docker run -p 5432:5432 -e POSTGRES_PASSWORD=x docker.io/library/postgres
```

Use `-d` to run it in the background and use `--name
bdi-stack-test-pg` to make it reusable with `docker start
bdi-stack-test-pg`.

Now the tests can be run using:

```sh
env AR_TEST_DB_NAME=postgres \
    AR_TEST_DB_USER=postgres \
    AR_TEST_DB_PASSWORD=x \
    make test
```

`clojure -M:test` runs all tests, `clojure -M:test COMPONENT...` runs
the tests for the specified components. For instance, to run only the
tests for `clj-ishare-client`, run `clojure -M:test clj-ishare-client`
in the top-level directory.

`make test` in the top-level directory runs all tests after ensuring
that the `test-config` is populated correctly.

## CHANGES

### v1.1.0-RC1
- Include BDI Connector in releases
- CI and release automation improvements
- Dependency updates
- Bug fixes

### v1.0.1
  - Added `authentication-service`, a standalone service that provides
    an access token endpoint generating trusted JWTs.
  - Improve logging
  - Internal refactoring: split out `service-commons`, improve tests.
    
### v1.0.0 
  - Initial public release

## Copying

Copyright (C) 2024-2025 Jomco B.V.

Copyright (C) 2024-2025 Stichting Connekt

[AGPL-3.0-or-later](LICENSES/AGPL-3.0-or-later.txt)
