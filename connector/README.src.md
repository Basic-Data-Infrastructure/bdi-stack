<!--
SPDX-FileCopyrightText: 2025 Jomco B.V.
SPDX-FileCopyrightText: 2025 Stichting Connekt
SPDX-License-Identifier: AGPL-3.0-or-later
-->
# BDI Connector

The BDI Connector gateway is a standalone service to authenticate and
authorize incoming HTTP requests. It can be configured to support
multiple authentication and authorization schemes compatible with the
[Basic Data Ifrastructure Trust Kit
architecture](https://bdi.gitbook.io/public/readme/trust-kit/authentication/authentication).

## ⚠ DISCLAIMER ⚠

**The software is for development and testing purposes only!**  It has
not been audited for security flaws and may not be suitable as a
starting point for production quality software.  Use at your own risk.

## Obtaining the BDI Connector

The BDI Connector is distributed as a standalone Java jar file and as
a docker image.

The jar file can be downloaded from [the BDI Stack releases page on
GitHub](https://github.com/Basic-Data-Infrastructure/bdi-stack/releases)
and can be run on a Java 21 runtime:

```sh
java -jar bdi-connector-VERSION.jar
```

The docker image can be downloaded and run using docker or podman as
`bdinetwork.azurecr.io/connector`:

```sh
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
- `#env!` same as `#env` but raises an error when the value is unset or blank
- `#private-key` read a private key from the given file name
- `#public-key` read a public key from the given file name
- `#x5c` read a certificate chain from the given file name

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

<!-- INCLUDE interceptors.md -->

## Evaluation

The arguments to interceptors will be evaluated before execution and can thus rely on vars or values put on `ctx` by earlier steps.  The evaluation support the following functions:

- `assoc`
- `assoc-in`
- `get`
- `get-in`
- `merge`
- `select-keys`
- `update`
- `update-in`
- `str`
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

### Error handling

The gateway will respond with "502 Bad Gateway" when an interceptor throws an exception.  When this happens the interceptor "error" phase handlers will be executed allowing for customized responses.

#### Example

The following example is protected by a basic authentication username / password and passes authenticated requests on to a backend which is also protected by basic authentication but with a different username / password.


```edn
{:rules [{:match {:headers {"authorization"
                            #join ["Basic " #b64 #join [#env! "USER" ":" #env! "PASS"]]}}
          :interceptors
          [[logger]
           [request update :headers assoc "authorization"
            #join ["Basic " #b64 #join [#env! "BACKEND_USER" ":" #env! "BACKEND_PASS"]]]
           [response update :headers assoc "x-bdi-connector" "passed"]
           [proxy (str "http://backend:port/" (get request :uri))]]}

         {:match        {}
          :interceptors [[logger]
                         [respond {:status  401
                                   :headers {"content-type" "text/plain"
                                             "www-authenticate" "Basic realm=\"secret\""}
                                   :body    "not allowed"}]]}]}
```

### WebSockets?

Not supported (yet).

## Forward Proxy

This connector can be used as a HTTP Forward Proxy but does not support HTTPS.  See [Connector HTTP(S) Forward Proxy](../connector-forward-proxy.md) for more information.

## Security considerations

### End-user header overrides

The connector sits between the consumer and the provider, any HTTP request header from the consumer is passed on to the provider thus sensitive headers which, for example, are used to allow access MUST be filtered out using the `request` or `bdi/deauthenticate` (for `X-Bdi-Client-Id`) interceptor.  For example:

```edn
[request update :headers dissoc "x-user-id"]
```

⚠ Headers case insensitive and always lower case in a request object, so when removing a header using `dissoc` use the lower case value! ⚠

### Strip tokens

Authentication an authorization tokens handled by the connector SHOULD be stripped before passing the request to a backend.  For example, when using `oauth2/bearer-token` interceptor, remove the "authorization" header immediately after.

```edn
[oauth2/bearer-token {:iss "http://example.com"
                      :aud "example"}
                     {:realm "example"}]
[request update :headers dissoc "authorization"]
```

⚠ Headers case insensitive and always lower case in a request object, so when removing a header using `dissoc` use the lower case value! ⚠

## Development

### Building the connector from source

The connector can be build from source as part of the BDI-Stack, by running

```sh
make bdi-connector.jar
```

in the root of this repository. See also [the "Developing" section in the top-level README file](../README.md#developing).

### Running the test suite

To run the test suite, run:

```sh
make test
```

On systems derived from BSD (like MacOS), the tests may timeout waiting to bind to `127.0.0.2`.  If that's the case, set up a loopback device on that address using something like (tested on OpenBSD and MacOS):

```sh
ifconfig lo0 alias 127.0.0.2 up
```
