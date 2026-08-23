# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src src

RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine AS java-runtime

FROM postgres:17-alpine
WORKDIR /app

COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apk add --no-cache curl \
    && mkdir -p /app/backups \
    && chown -R postgres:postgres /app

COPY --from=build --chown=postgres:postgres /app/target/*.jar app.jar

USER postgres

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
