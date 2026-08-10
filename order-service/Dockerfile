# syntax=docker/dockerfile:1
#
# Build context is the repository root (see docker-compose.yml) so this stage
# can reach the shared proto/ directory and the Gradle multi-module build.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY proto proto
COPY order-service order-service
COPY payment-service payment-service

RUN chmod +x gradlew && ./gradlew :order-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/order-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
