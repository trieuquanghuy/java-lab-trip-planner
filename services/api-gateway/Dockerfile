# Multi-stage backend Dockerfile — builds the bootJar INSIDE the container.
# Build context: repo root (set in compose via build.context: .. and
# build.dockerfile: services/api-gateway/Dockerfile).
# Satisfies ROADMAP SC#1 "no manual intervention" — no prior `./gradlew bootJar`
# required from a fresh checkout.

# Stage 1: build with Gradle multi-module repo root as build context.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache bash
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew
COPY libs libs
COPY services/api-gateway services/api-gateway
RUN --mount=type=cache,target=/root/.gradle ./gradlew :services:api-gateway:bootJar -x test --no-daemon

# Stage 2: slim runtime.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=build /workspace/services/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=6 --start-period=20s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
