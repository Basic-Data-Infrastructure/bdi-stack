<!--
SPDX-FileCopyrightText: 2025 Jomco B.V.
SPDX-FileCopyrightText: 2025 Topsector Logistiek
SPDX-License-Identifier: AGPL-3.0-or-later
-->
# Connector

## Reverse Proxy

TODO

### Websockets?

Not supported (yet).

## Running the test suite

To run the test suite execute:

```sh
clojure -M:test
```

On systems derived from BSD (like MacOS) the tests may timeout waiting
to bind to `127.0.0.2`.  If that's the case setup a loopback device on
that address using something like (tested on OpenBSD):

```sh
ifconfig lo0 alias 127.0.0.2 up
```
