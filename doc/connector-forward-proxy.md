<!--
SPDX-FileCopyrightText: 2025 Jomco B.V.
SPDX-FileCopyrightText: 2025 Stichting Connekt
SPDX-License-Identifier: AGPL-3.0-or-later
-->
# Connector HTTP(S) Forward Proxy

A proposed component of the BDI connector is an HTTP(S) forward proxy.  An *HTTP forward proxy* acts as an intermediary between a client, such as a web browser, and the BDI network. This proxy intercepts outgoing requests from the client and forwards them to the intended destination within the network. According to the BDI guidelines, enrichments to provide authentication and authorization are added to the request based on the proxy configuration. The proxy then receives the response from the server in the network and returns it to the client.

HTTP(S) clients, including web browsers and software components for HTTP operations, can be configured to use such a proxy, making the operation of the proxy transparent to the end user.

## HTTPS, MITM, and Tunnels

The connector proxy positions itself as an intermediary, which in effect creates a Man-In-The-Middle (MITM) scenario. A MITM attack is a cyberattack in which a malicious actor intercepts and potentially manipulates the communication between two parties without the parties involved being aware of it. The goal is to obtain sensitive information or modify data.

HTTPS is designed to make MITM attacks more difficult through *encryption* and *authentication*. It uses *root certificates* to verify the identity of the server, and TLS encryption to protect communication from unauthorized access and manipulation. An attacker cannot eavesdrop on or manipulate the communication without the server's private key or a compromised root certificate. A root certificate is a public *trust anchor* with which the server's encryption/authentication certificate is digitally signed to prove that the server is the rightful owner of the certificate it is using and that the communication is secure.

Although an HTTPS client can be configured to use a proxy, in the case of HTTPS traffic, the proxy cannot simply accept, modify, and forward a request. HTTPS clients that do allow this are considered insecure.  Instead, the HTTP method `CONNECT` has been introduced, which requests the proxy to open a *tunnel* to the target server. This tunnel is a TCP/IP connection within which a TLS connection can be established between the client and the server. The proxy, by the nature of a TLS connection, has no ability to view or modify data.

## Problems and Solution Directions

The nature of HTTPS connections and forward proxy protocols makes it difficult to introduce BDI enrichments into the traffic between a client and a server, since all traffic in a BDI network passes through secure connections, such as HTTPS.  Here are some solutions to mitigate these difficulties.

### Enterprise Root CA

A common solution is the implementation of a self-introduced Certificate Authority (CA). This CA is then used to digitally sign pseudo-certificates on behalf of the servers in the BDI network.

This pseudo-certificate allows the connector proxy to split the tunnel into a connection between the client and the proxy (with a *pseudo*-certificate), and a connection (with a *real* certificate) between the proxy and the server. In this configuration, the proxy can view and modify the request, after which it is re-encrypted and forwarded to the server.

Advantages:

- The connector proxy operates completely transparently.

Disadvantages:

-  Management of a self-introduced Certificate Authority.

   The implementation of a component that functions as a Certificate Authority requires management of secret keys, for which, for example, a Hardware Security Module (HSM) should be used.

-  Certificate Authority as trust anchor.

   The root certificates of the Certificate Authority must be added as trusted root certificates on the systems that use the connector proxy.

-  Generating pseudo-certificates.

   The proxy must be able to generate signed pseudo-certificates based on the certificates of the self-introduced Certificate Authority.

### Internal HTTP, external HTTPS

Since standard HTTP traffic (without encryption) does offer the possibility of viewing and modifying, it is possible to implement unencrypted HTTP traffic between the connector proxy and the client and to translate it in the proxy to HTTPS traffic (with encryption) to the server. The translation can be arranged in the following ways:

- A domain name hint.

  For example, if the target location is `https://api.bdi-example.nl/data`, the client uses `http://api.bdi-example.nl-ssl/data`. Here, `http` is used instead of `https` and an `-ssl` suffix is added to the domain name. The proxy automatically performs the translation based on the presence of the suffix.

- A translation list.

  The proxy can be configured with a list of domain names for which HTTP should be switched to HTTPS.

- Always translate HTTP to HTTPS.

  Since all communication in a BDI network takes place via encrypted connections, it is possible to translate all incoming requests from HTTP to HTTPS.

Advantages:

- Easy to implement.

- Still works when client code is generated from, for instance, OpenAPI Specs.

Disadvantages:

- Error-prone.

  The client-side configuration is very error-prone because the URLs to servers in the BDI network are very similar to the *actual* URLs to servers on the BDI network.

- Leaks of unencrypted traffic.

  In the event of misconfiguration or the absence of servers on the translation list, requests may be passed through without the HTTP to HTTPS translation, causing information to be sent out unencrypted.  This must be prevented by the connector proxy.

- Internal traffic is unencrypted.

  In this approach, it is impossible to encrypt traffic between the client and the proxy, so both must be in a trusted or private network.

### Connector API

An alternative solution is to avoid a forward proxy implementation and instead offer an API on the connector that implements clearly specified interactions in the BDI network for the benefit of clients in the internal network.

The example below illustrates a GET request to `https://api.bdi-example.nl/data`, which will be enriched with an *access token* and any *delegation evidence* for the receiving party known as `EORI-1234`.

```
GET /remote/EORI-1234/api.bdi-example.nl/data
```

Advantages:

- No (potentially complex) proxy configuration for clients.

- Easier to debug.

  With a proxy setup, it is easy to lose sight of it, which means that problems may be sought in the wrong place.

- Internal traffic can be encrypted.

  This approach makes it possible to set up SSL termination for the connector with, for example, NginX or Caddy.

- Freedom to specify a clear domain- or party-specific API.

Disadvantages:

- A domain- or party-specific API must be specified.

- There is more management and maintenance pressure on the connector.

  Each new method of authentication, authorization or communication may require adjustments to the specification made.

- Does not work when client code is generated from, for instance, OpenAPI Specs.

  It will require to make specifications for the created API which allow code generation.

## Conclusion

The connector API seems to be the most suitable solution for the forward proxy problem and offers opportunities for the standardization of BDI networks because it can enforce a specification.
