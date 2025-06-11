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

- `response` produces a literal response in the "entering" phase.

- `request/rewrite` rewrites the server part of the request to the given URL.

- `request/update` evaluates an update on the request in the "entering" phase, includes `request` in the evaluation environment.

- `response/update` evaluates an update on the response in the "leaving" phase, includes `request` and `response` in the evaluation environment.

- `reverse-proxy/forwarded-headers` record information for "x-forwarded" headers on the request in the "entering" phase on the `:proxy-request-overrides`.  Note: put this interceptor near the top to prevent overwriting request properties by other interceptors like `request/update`.

- `reverse-proxy/proxy-request` produce a response by executing the (modified!) request (including the recorded "x-forwarded" headers information in `:proxy-request-overrides`) in the "entering" phase.

- `bdi/authenticate` validate bearer token on incoming request, when none given responds with "401 Unauthorized", otherwise adds "X-Bdi-Client-Id" request header for consumption downstream.

- `bdi/deauthenticate` ensure the "X-Bdi-Client-Id" request header is **not** already set on a request for public endpoints which do not need authentication.  This prevents clients from fooling the backend into being authenticated.

- `bdi/connect-token` provide a access token (M2M) endpoint to provide access tokens.  Note: this interceptor does no matching, so it needs to be added to a separate rule with a match like: `{:uri "/connect/token", :request-method :post}`.

The "eval" interceptors support the following functions:

- `assoc`
- `assoc-in`
- `get`
- `merge`
- `select-keys`
- `str`
- `update`

Here are example rules for a minimal reverse proxy to [httpbin](https://httpbin.org):

```edn
[[reverse-proxy/forwarded-headers]
 [request/rewrite "https://httpbin.org"]
 [reverse-proxy/proxy-request]]
```

#### Example

The following example is protected by a basic authentication username / password and passes authenticated requests on to a backend which is also protected by basic authentication but with a different username / password.


```edn
{:rules [{:match {:headers {"authorization"
                            #join ["Basic " #b64 #join [#env "USER" ":" #env "PASS"]]}}
          :interceptors [[reverse-proxy/forwarded-headers]
                         [request/update assoc
                          :scheme #keyword #env "BACKEND_PROTO"
                          :server-name #env "BACKEND_HOST"
                          :server-port #long #env "BACKEND_PORT"]
                         [request/update update :headers assoc "authorization"
                          #join ["Basic " #b64 #join [#env "BACKEND_USER" ":" #env "BACKEND_PASS"]]]
                         [response/update update :headers assoc "x-bdi-connector" "passed"]
                         [reverse-proxy/proxy-request]]}

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
