<!--
SPDX-FileCopyrightText: 2025 Jomco B.V.
SPDX-FileCopyrightText: 2025 Topsector Logistiek
SPDX-License-Identifier: AGPL-3.0-or-later
-->
# Connector

## Gateway

The gateway requires the following environment variables:

- `RULES_FILE`

  The file of an EDN file describing the routing rules (see also section "Rules").

- `HOSTNAME`

  The hostname to listen on; defaults to `localhost`.

- `PORT`

  The port number to listen on; defaults to `8081`.

To start it, run:

```sh
make bdi-connector.jar
java -jar bdi-connector.jar
```

or:

```sh
clojure -M:run
```

### Rules

The rules file is parsed using [aero](https://github.com/juxt/aero) and is extended with the following tag literals:

- `#rx` to produce regular expressions
- `#b64` to produce base64 encoded strings

Top-level configuration:

- `:vars` used to globally extend the evaluation context for the "eval" interceptors
- `:rules` a list of rules to be evaluated top to bottom when handling a request

A rule contains:

- `:match` a (partial) request object for matching an incoming request
- `:interceptors` a list of interceptors to apply to an incoming request and produce a response
- `:vars` (optional) rule-specific vars to extend the evaluation context of "eval" interceptors

When no rule matches an incoming request, the gateway will respond with `404 Not Found`.

#### Match

Incoming requests are shaped as described in the [Ring Spec](https://github.com/ring-clojure/ring/blob/master/SPEC.md).  A match expression describes the minimal properties a request must have to pass and allows capturing values from the request into vars.

Maps, strings, vectors and keywords are to match exactly.  Regular expressions will be applied to strings for matches and symbols will be allowed as placeholders to capture vars.

The following will match all `GET` requests:

```edn
{:request-method :get}
```

All requests to some path starting with `/foo/bar` and capture the referer URL in the `?referer` var.  Note that header names are case-insensitive, so a lowercase name is used to match.

```edn
{:uri #rx "/foo/bar.*"
 :headers {"referer" ?referer}}
```

#### Interceptors

An interceptor operates on either the "entering" or "leaving" phase of an interaction, or both.  In the "entering" phase no response has been formulated yet.  When an interceptor does produce a response in the "entering" phase the already visited interceptors are executed in the reverse order; this is the "leaving" phase.

This gateway comes with the following base interceptors:

- `response` produces a literal response in the "entering" phase

- `request/eval` evaluates an update on the request in the "entering" phase

- `response/eval` evaluates an update on the response in the "leaving" phase

- `reverse-proxy/forwarded-headers` record information for "x-forwarded" headers on the request in the "entering" phase

- `reverse-proxy/proxy-request` produce a response by executing the (modified!) request (including the recorded "x-forwarded" headers information) in the "entering" phase

Here are example rules for a minimal reverse proxy to [httpbin](https://httpbin.org):

```edn
[reverse-proxy/forwarded-headers
 [request/eval assoc
  :scheme :https, :server-name "httpbin.org", :server-port 443]
 reverse-proxy/proxy-request]
```

Note that if the backend relies on virtual hosting, the ["host" header](https://developer.mozilla.org/en-US/docs/Glossary/Host) needs to be added.

```edn
[reverse-proxy/forwarded-headers
 [request/eval assoc
  :scheme :https, :server-name "httpbin.org", :server-port 443]
 [request/eval update :headers assoc "host" "httpbin.org"]
 reverse-proxy/proxy-request]
```

An alternative way of rewriting the request is to provide an `:url` on the request:

```edn
[request/eval assoc :url
 (str "https://httpbin.org/"
      (get request :uri)
      "?" (get request :query-string))]
```

#### Example

The following example is protected by a basic authentication username / password and passes authenticated requests on to a backend which is also protected by basic authentication but with a different username / password.


```edn
{:rules [{:match {:headers {"authorization"
                            ["Basic " #b64 #join [#env "USER" ":" #env "PASS"]]}}
          :interceptors [reverse-proxy/forwarded-headers
                         [request/eval assoc
                          :scheme #keyword #env "BACKEND_PROTO"
                          :server-name #env "BACKEND_HOST"
                          :server-port #long #env "BACKEND_PORT"]
                         [request/eval update :headers assoc "authorization"
                          ["Basic " #b64 #join [#env "BACKEND_USER" ":" #env "BACKEND_PASS"]]]
                         [response/eval update :headers assoc "x-bdi-connector" "passed"]
                         reverse-proxy/proxy-request]}

         {:match        {}
          :interceptors [[respond {:status  401
                                   :headers {"content-type" "text/plain"
                                             "www-authenticate" "Basic realm=\"secret\""}
                                   :body    "not allowed"}]]}]}
```

### WebSockets?

Not supported (yet).

## Running the test suite

To run the test suite, run:

```sh
clojure -M:test
```

On systems derived from BSD (like MacOS), the tests may timeout waiting to bind to `127.0.0.2`.  If that's the case, set up a loopback device on that address using something like (tested on OpenBSD and MacOS):

```sh
ifconfig lo0 alias 127.0.0.2 up
```
