<!--
SPDX-FileCopyrightText: 2025 Jomco B.V.
SPDX-FileCopyrightText: 2025 Topsector Logistiek
SPDX-License-Identifier: AGPL-3.0-or-later
-->
# BDI Connector

The BDI Connector gateway is a standalone service to authenticate and
authorize incoming HTTP requests. It can be configured to support
multiple authentication and authorization schemes compatible with the
[Basic Data Ifrastructure Trust Kit
architecture](https://bdi.gitbook.io/public/readme/trust-kit/authentication/authentication).

## Obtaining the BDI Connector

The BDI Connector is distributed as a standalone Java jar file and as
a docker image.

The jar file can be downloaded from [the BDI Stack releases page on
GitHub](https://github.com/Basic-Data-Infrastructure/bdi-stack/releases)
and can be run on a Java 21 runtime:

```
java -jar bdi-connector-VERSION.jar
```

The docker image can be downloaded and run using docker or podman as
`bdinetwork.azurecr.io/connector`:

```
docker run bdinetwork.azurecr.io/connector:VERSION
```

## Configuring the Gateway

The gateway requires the following environment variables:

- `RULES_FILE`

  The file of an EDN file describing the routing rules (see also section "Rules").

- `HOSTNAME`

  The hostname to listen on; defaults to `localhost`.

- `PORT`

  The port number to listen on; defaults to `8081`.

### Rules

The rules file is parsed using [aero](https://github.com/juxt/aero) and is extended with the following tag literals:

- `#rx` to produce regular expressions
- `#b64` to produce base64 encoded strings

Top-level configuration:

- `:vars` used to globally extend the evaluation context for the "eval" interceptors
- `:rules` a list of rules to be matched and evaluated top to bottom when handling a request

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
{:uri     #rx "/foo/bar.*"
 :headers {"referer" ?referer}}
```

#### Interceptors

An interceptor operates on either the "entering" or "leaving" / "error" phase of an interaction, or both.  In the "entering" phase no response has been formulated yet.  When an interceptor does produce a response or an exception is raise in the "entering" phase the already visited interceptors are executed in the reverse order; this is the "leaving" or in case of an exception "error" phase.

This gateway comes with the following base interceptors:

- `logger` logs incoming requests (method, url and protocol) and response (status and duration) at `info` level in the "leaving" / "error" phase.  Note: put this interception in the first position to get proper duration information.

   Example:

   - `GET http://localhost:8081/ HTTP/1.1 / 200 OK / 370ms`

  Passing an extra expression will add MDC (Mapped Diagnostic Context) to the request log line with evaluated data.

  Example:

  - match: `:match {:query-params {"pageNr" page-nr}}`
  - interceptor: `[logger {"page-nr" page-nr, "uri" (:uri request)}]`
  - request: `http://localhost:8081/test?pageNr=31415`
  - log line: `GET http://localhost:8081/ HTTP/1.1 / 200 OK / 370ms page-nr=31415, uri="/test"`

- `response` produces a literal response in the "entering" phase by evaluation the given expression.

  Example:

  ```
  [response {:status 200, :body "hello world"}]
  ```

- `request/update` evaluates an update on the request in the "entering" phase, includes `request` and `ctx` in the evaluation environment.

  Example:

  ```
  [request/update assoc-in [:headers "x-request"] "updated"]
  ```

- `response/update` evaluates an update on the response in the "leaving" phase, includes `request`,  `response` and `ctx` in the evaluation environment.

  Example:

  ```
  [response/update assoc-in [:headers "x-response"] "updated"]
  ```

- `request/rewrite` rewrites the server part of the request to the given URL in preparation of the `reverse-proxy/proxy-request` interceptor.

  Example:

  ```
  [request/rewrite "http://example.com"]
  ```

- `reverse-proxy/forwarded-headers` record information for "x-forwarded" headers on the request in the "entering" phase on the `:proxy-request-overrides`.  Note: put this interceptor near the top to prevent overwriting request properties by other interceptors like `request/update`.

- `reverse-proxy/proxy-request` produce a response by executing the (rewritten) request (including the recorded "x-forwarded" headers information in `:proxy-request-overrides`) in the "entering" phase.

  Here are example rules for a minimal reverse proxy to [httpbin](https://httpbin.org):

  ```edn
  [[reverse-proxy/forwarded-headers]
   [request/rewrite "https://httpbin.org"]
   [reverse-proxy/proxy-request]]
  ```

- `oauth2/bearer-token` require an OAuth 2.0 Bearer token with the given requirements and auth-params for a 401 Unauthorized response.  The absence of a token or it not complying to the requirements causes a 401 Unauthorized response.  At least the audience `:aud` and issuer `:iss` should be supplied to validate the token.  The JWKs are derived from the issuer openid-configuration (issuer is expected to be a URL and the well-known suffix is appended), if not available `jwks-uri` should be supplied.  The claims in the token will be available through var `oauth2/claims`.

  The following example expects a token from example.com and responds with "Hello subject" where "subject" is the "sub" of the token.

  ```
  [oauth2/bearer-token {:iss "http://example.com"
                        :aud "example"}
                       {:realm "example"}]
  [response/update assoc :body (str "Hello " (get oauth2/claims :sub))]
  [respond {:status 200}]
  ```

  The claims for a valid access token will be place in `ctx` property `:oauth2/bearer-token-claims`.
  
  Both arguments to this intercepted are evaluated as an expression and includes `request` and `ctx` in the evaluation environment.

- `bdi/authenticate` validate bearer token on incoming request, when none given responds with "401 Unauthorized", otherwise adds "X-Bdi-Client-Id" request header and vars for consumption downstream.  Note: put this interceptor *before* `logger` when logging the client-id.

- `bdi/deauthenticate` ensure the "X-Bdi-Client-Id" request header is **not** already set on a request for public endpoints which do not need authentication.  This prevents clients from fooling the backend into being authenticated.  **Always use this on public routes when authentication is optional downstream.**

- `bdi/connect-token` provide a access token (M2M) endpoint to provide access tokens.  Note: this interceptor does no matching, so it needs to be added to a separate rule with a match like: `{:uri "/connect/token", :request-method :post}`.

  Example:

  ```
  {:match        {:uri "/connect/token"}
   :interceptors [[bdi/connect-token]]}
  ```

## Evaluation

The arguments to interceptors will be evaluated before execution and can thus rely on vars or values put on `ctx` by earlier steps.  The evaluation support the following functions:

- `assoc`
- `assoc-in`
- `get`
- `get-in`
- `merge`
- `select-keys`
- `str`
- `update`
- `update-in`
- `str/replace`
- `str/lower-case`
- `str/upper-case`
- `=`
- `not`

and special forms:

- `if`
- `or`
- `and`

and have access to the following vars:

- `ctx`
- `request`
- `response` (when already available)
- and all `vars` defined globally, on a rule
- and captured by `match`.

The `response` is only available when it's not an *async* object like the result of the `reverse-proxy/proxy-request` interceptor.

#### Example

The following example is protected by a basic authentication username / password and passes authenticated requests on to a backend which is also protected by basic authentication but with a different username / password.


```edn
{:rules [{:match {:headers {"authorization"
                            #join ["Basic " #b64 #join [#env "USER" ":" #env "PASS"]]}}
          :interceptors [[logger]
                         [reverse-proxy/forwarded-headers]
                         [request/update assoc
                          :scheme #keyword #env "BACKEND_PROTO"
                          :server-name #env "BACKEND_HOST"
                          :server-port #long #env "BACKEND_PORT"]
                         [request/update update :headers assoc "authorization"
                          #join ["Basic " #b64 #join [#env "BACKEND_USER" ":" #env "BACKEND_PASS"]]]
                         [response/update update :headers assoc "x-bdi-connector" "passed"]
                         [reverse-proxy/proxy-request]]}

         {:match        {}
          :interceptors [[logger]
                         [respond {:status  401
                                   :headers {"content-type" "text/plain"
                                             "www-authenticate" "Basic realm=\"secret\""}
                                   :body    "not allowed"}]]}]}
```

### WebSockets?

Not supported (yet).

## Building the connector from source

The connector can be build from source as part of the BDI-Stack, by running

```sh
make bdi-connector.jar
```

in the root of this repository. See also [the "Developing" section in the top-level README file](../README.md#developing).

## Running the test suite

To run the test suite, run:

```sh
clojure -M:test
```

On systems derived from BSD (like MacOS), the tests may timeout waiting to bind to `127.0.0.2`.  If that's the case, set up a loopback device on that address using something like (tested on OpenBSD and MacOS):

```sh
ifconfig lo0 alias 127.0.0.2 up
```
