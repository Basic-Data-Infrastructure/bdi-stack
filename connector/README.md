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

### Rules

```edn
{:vars {backend-url "http://localhost:3000"
        auth        #b64 "demo1:31415"}

 :rules [{:match    {:method :get
                     :uri    #rx "something/.*"}
          :pipeline [
                     [bdi/authenticate]
                     [request/eval assoc :scheme :http, :server-name "localhost", :server-port 3000]
                     [request/eval update :headers select-keys ["accept"
                                                                "accept-encoding"
                                                                "accept-language"
                                                                "host"
                                                                "connection"
                                                                "cookie"
                                                                "cache-control"
                                                                "pragma"
                                                                "content-type"
                                                                "content-length"
                                                                "if-modified-since"
                                                                "if-match"
                                                                "x-requested-with"]]
                     [request/eval update :headers merge {"authorization" (str "Basic " auth)}]
                     [reverse-proxy]
                     [response/eval update :headers select-keys ["set-cookie"
                                                                 "cache-control"
                                                                 "content-type"
                                                                 "content-length"
                                                                 "location"
                                                                 "date"
                                                                 "etag"
                                                                 "vary"]]
                     [response/eval update :headers assoc "x-bdi" "passed"]]}

         {:match    _
          :pipeline [[respond {:status  404
                               :headers {"content-type" "text/plain"}
                               :body    "not found"}]]}]}
```

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
