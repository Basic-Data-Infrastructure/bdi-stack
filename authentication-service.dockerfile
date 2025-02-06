# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

FROM clojure:tools-deps as builder

RUN mkdir /bdi-stack
WORKDIR /bdi-stack
COPY . /bdi-stack
WORKDIR /bdi-stack/authentication-service

RUN make bdi-authentication-service.jar

FROM gcr.io/distroless/java21-debian12
COPY --from=builder /bdi-stack/authentication-service/bdi-authentication-service.jar /bdi-authentication-service.jar

WORKDIR /
ENTRYPOINT ["java", "-jar", "bdi-authentication-service.jar"]
