<!--
SPDX-FileCopyrightText: 2025 Jomco B.V.
SPDX-FileCopyrightText: 2025 Stichting Connekt
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->
# Deployment BDI Connector

The following configuration files are needed for deployment:

- `config/rules.edn`: This file contains the rules set for the connector gateway. The rules define how the connector handles requests and responses to and from the backend API. The `.edn` extension indicates that the file is in [Extensible Data Notation](https://github.com/edn-format/edn), a data serialization format.

- `environment`: This file contains the environment variables needed to run the connector. These variables configure settings such as network ports, logging levels, access credentials, and the location of related servers.

## Overview

The goal of the BDI Connector is to expose a backend API in a private network to an application on the internet by applying authentication and authorization rules. This ensures that only authorized applications can access the backend API.

```
                  +-------------+
                  | Backend API |
                  +-------------+
                         ↕
 +----------------------------------------------+
 |                 BDI Connector                |
 +----------------------------------------------+
        ↕                |                |
 +---------------+       |                |
 | Load Balancer |       |                |
 +---------------+       |                |
        ↕                ↓                ↓        Private network
--------- ------------ firewall -----------------------------------
        ↕                ↓                ↓        Internet
 +-------------+ +----------------+ +-----------+
 | Application | | OAuth Provider | | Noodlebar |
 +-------------+ +----------------+ +-----------+
```

- Backend API

   Provides the API being exposed.  It runs inside the private network, providing data and functionality that needs to be secured.

- BDI Connector

  This component runs inside the private network and needs to be able to connect to the Backend API, OAuth Provider and Noodlebar.  It acts as a gateway, enforcing authentication and authorization policies.

- Load Balancer

  A load balancer, front proxy or some other gateway to handle HTTPS/TLS and exposure to the internet.  This can be anything like NGINX, HAProxy or a component provided by a cloud provider.  It runs in the private network or on the internet, depending on the setup, and is exposed to the internet.

- Application

  The application processing the data from the backend API.  It runs outside the private network and uses BDI methods for authentication and authorization.  This is the client application that consumes the API.

- OAuth Provider

  OAuth Provider for authentication.  This service verifies the identity of the application trying to access the Backend API.
  
- Noodlebar

  Authorization register.  This component determines what resources the authenticated application is allowed to access.

Important notes:

- The BDI Connector needs egress access to the Backend API, OAuth Provider and Noodlebar.  This means the connector must be able to initiate connections to these services.

- The BDI Connector does *not* handle HTTPS/SSL requests, only HTTP.  TLS termination is expected to be handled by the Load Balancer.

- The Backend API, BDI Connector and Load Balancer are in scope for this deployment.  These components are the focus of the deployment and configuration process described in this document.

## Running

How to run the connector depends on the technology stack available.   The most common options are described below.

### Container

The BDI Connector can also be run as a container using Docker or Podman.  Here's an example using Docker:

```
docker run \
  --env-file=$(pwd)/environment \
  --mount type=bind,src=$(pwd)/config,dst=/config \
  --publish=8080:5004 \
  bdinetwork.azurecr.io/connector:TAG
```

- `--env-file=$(pwd)/environment`: This option loads environment variables from the `environment` file in the current directory.
- `--mount type=bind,src=$(pwd)/config,dst=/config`: This option mounts the `config` directory in the current directory to the `/config` directory inside the container. This allows the container to access the `rules.edn` file.
- `--publish=8080:5004`: This option maps port 8080 on the host to port 5004 inside the container, allowing access to the connector from the host.
- `bdinetwork.azurecr.io/connector:TAG`: This is the Docker image name for the BDI Connector.  `TAG` should be replaced with the correct tag.

Note: The second port number used with the `--publish` argument matches the `PORT` variable in the environment variables file.  Also, the use of `$(pwd)` in this command assumes the command runs in this directory.

### JVM (Java Virtual Machine)

To download the BDI Connector, go to the [BDI Stack releases page](https://github.com/Basic-Data-Infrastructure/bdi-stack/releases/) and download the `bdi-connector-TAG.zip` asset.  This zip-file contains a file named `bdi-connector.jar`.

Run it using the `environment` file in this directory as follows:

```sh
set -a
source environment
java -jar bdi-connector.jar
```

- `set -a`: This command marks all variables as exported, meaning any variable assignment, like `FOO=bar`, will be exported to the environment of subsequently executed commands.
- `source environment`: This command reads and executes the commands from the `environment` file, in this case, setting the environment variables.
- `java -jar bdi-connector.jar`: This command executes the BDI Connector JAR file using the Java runtime.

Note: Running the BDI Connector requires Java 21 or higher.

## Logging

Logging is based on [logback](https://logback.qos.ch/).  It produces plain text lines to standard output and a log file named `audit.json` for demo purposes.  The latter uses the [JSON Encoder](https://logback.qos.ch/manual/encoders.html#JsonEncoder), which logs a JSON object message per line with a specific scope to be used for the audit log demo.  The log level can be adjusted using the `LOG_LEVEL` environment variable and defaults to `info`.

Logging can be configured by setting:

```
JAVA_TOOL_OPTIONS="-Dlogback.configurationFile=/path/to/logback.xml"
```

in the environment file.  This allows you to specify a custom Logback configuration file. The `JAVA_TOOL_OPTIONS` environment variable is used to pass options to the JVM.

The current `logback.xml` file is part of the JAR file and can be extracted with:

```sh
jar xvf connector/bdi-connector.jar logback.xml
```

Read the [Configuration file syntax in the logback manual](https://logback.qos.ch/manual/configuration.html#syntax) for details.

## Security considerations

### Network access

The BDI Connector *only* needs access to: the Backend API, the OAuth provider and the Noodlebar.  Other access should be blocked.  This principle of least privilege minimizes the potential attack surface.

### Rate limiting

To protect the Backend API from DoS (Denial of Service) attacks, apply rate limiting in the load balancer or firewall.  The rate depends on the expected load and the maximum load the Backend API services can handle.  Rate limiting restricts the number of requests a client can make within a certain time period.

### Monitoring

Add the BDI Connector or the Load Balancer to your SIEM (Security Information and Event Management) to monitor its performance.   A high error rate may indicate attackers are trying to gain access to the Backend API or that the configuration is incorrect.

A health check, to see if the connector is running, can be performed by doing an HTTP GET request on `/ping`, which should respond with a `200 OK` and the body `pong`.  Note: This endpoint only confirms the connector is running but not that the configuration is working.
