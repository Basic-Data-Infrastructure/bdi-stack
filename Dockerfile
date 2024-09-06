FROM clojure:tools-deps as builder
RUN apt-get -y update
RUN apt-get install -y curl

RUN mkdir /app
WORKDIR /app
COPY . /app/
RUN make bdi-authorization-register.jar


FROM gcr.io/distroless/java21-debian12
COPY --from=builder /app/bdi-authorization-register.jar /bdi-authorization-register.jar

WORKDIR /
ENTRYPOINT ["java", "-jar", "bdi-authorization-register.jar"]
