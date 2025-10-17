# SPDX-FileCopyrightText: 2025 Jomco B.V.
# SPDX-FileCopyrightText: 2025 Stichting Connekt
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

FROM clojure:tools-deps as builder

RUN mkdir /bdi-stack
WORKDIR /bdi-stack
COPY . /bdi-stack
WORKDIR /bdi-stack/connector

RUN make bdi-connector.jar

FROM gcr.io/distroless/java21-debian12
COPY --from=builder /bdi-stack/connector/bdi-connector.jar /bdi-connector.jar

WORKDIR /
ENTRYPOINT ["java", "-jar", "bdi-connector.jar"]
