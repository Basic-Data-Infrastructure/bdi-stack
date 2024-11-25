<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Topsector Logistiek
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# BDI Stack

Tools for developing clients and services in the [Basic Data
Infrastructure](https://bdinetwork.org/) framework.

The BDI Stack contains standalone services and libraries.

## Services

- [association-register](./association-register) - A minimal iSHARE /
  BDI compatible Association Register
- [authorization-register](./authorization-register) - A minimal
  iSHARE / BDI compatible Authorization Register

## Libraries

- [clj-share-jwt](./clj-ishare-jwt) - A Clojure Library for creating
  and validating iSHARE compatible JWTs
- [clj-share-client](./clj-ishare-client) - A Clojure Library for
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

`clojure -M:test` runs all tests, `clojure -M:test COMPONENT...` runs
the tests for the specified components. For instance, to run only the
tests for `clj-ishare-client`, run `clojure -M:test clj-ishare-client`
in the top-level directory.

`make test` in the top-level directory runs all tests after ensuring
that the `test-config` is populated correctly.

## Copying

Copyright (C) 2024 Jomco B.V.

Copyright (C) 2024 Topsector Logistiek

[AGPL-3.0-or-later](LICENSES/AGPL-3.0-or-later.txt)
