FROM clojure:tools-deps as builder
RUN apt-get -y update
RUN apt-get install -y curl

RUN mkdir /app
WORKDIR /app
COPY deps.edn /app/
RUN clj -X:deps list # force download of deps so we can cache it if deps.edn hasn't changed

COPY . /app/
RUN make bdi-association-register.jar


FROM gcr.io/distroless/java21-debian12
COPY --from=builder /app/bdi-association-register.jar /bdi-association-register.jar

WORKDIR /
ENTRYPOINT ["java", "-jar", "bdi-association-register.jar"]
