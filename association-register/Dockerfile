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
COPY deps.edn /app/
RUN clojure -X:deps list # force download of deps so we can cache it if deps.edn hasn't changed

COPY . /app/
RUN make bdi-association-register.jar


FROM gcr.io/distroless/java21-debian12
COPY --from=builder /app/bdi-association-register.jar /bdi-association-register.jar
COPY --from=builder /app/logback.xml /logback.xml

WORKDIR /
ENTRYPOINT ["java", "-jar", "bdi-association-register.jar"]
