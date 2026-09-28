# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package && cp target/core-api-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
# UID/GID 10001 — как у media-worker: оба пишут в общий volume локального хранилища (STORAGE_DRIVER=local).
RUN addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app && apk add --no-cache wget \
    && mkdir -p /data/files && chown 10001:10001 /data/files
ENV STORAGE_LOCAL_ROOT=/data/files
USER 10001:10001
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
