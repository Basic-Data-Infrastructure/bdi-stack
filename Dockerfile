# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

FROM clojure:tools-deps as builder
RUN apt-get -y update
RUN apt-get install -y curl

RUN mkdir /app
WORKDIR /app
COPY deps.edn /app

RUN clj -X:deps list # force download of deps so we can cache it if deps.edn hasn't changed

COPY . /app/
RUN make bdi-authorization-register.jar

FROM gcr.io/distroless/java21-debian12
COPY --from=builder /app/bdi-authorization-register.jar /bdi-authorization-register.jar

WORKDIR /
ENTRYPOINT ["java", "-jar", "bdi-authorization-register.jar"]
