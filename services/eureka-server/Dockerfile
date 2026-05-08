# Multi-stage backend Dockerfile — builds the bootJar INSIDE the container.
# Build context: repo root (set in compose via build.context: .. and
# build.dockerfile: services/eureka-server/Dockerfile).
# Satisfies ROADMAP SC#1 "no manual intervention" — no prior `./gradlew bootJar`
# required from a fresh checkout.

# Stage 1: build with Gradle multi-module repo root as build context.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
# Install bash for the gradle wrapper (alpine ships with ash by default; gradlew
# works under ash but bash is safer).
RUN apk add --no-cache bash
# Copy build infrastructure first so dep-resolution layer is cached when only
# sources change.
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
# Make gradlew executable in case file mode was lost (e.g. via Windows checkout).
RUN chmod +x gradlew
# Copy shared libs (libs/observability, libs/error-handling, libs/api-contracts).
COPY libs libs
# Copy ONLY this service's sources to keep cache-busting tight.
COPY services/eureka-server services/eureka-server
# Build only this service's bootJar; skip tests in the image build (CI runs them).
# The --no-daemon flag is recommended in containers (no daemon to reuse).
# `--mount=type=cache` is a BuildKit feature; if BuildKit is unavailable the
# build still works just slower.
RUN --mount=type=cache,target=/root/.gradle ./gradlew :services:eureka-server:bootJar -x test --no-daemon

# Stage 2: slim runtime.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
# wget is needed for the HEALTHCHECK below; alpine base ships without it.
RUN apk add --no-cache wget
COPY --from=build /workspace/services/eureka-server/build/libs/*.jar app.jar
EXPOSE 8761
# Eureka self-bootstrap takes slightly longer than other services — use a
# 25s start-period (vs the standard 20s) to absorb its registry init.
HEALTHCHECK --interval=10s --timeout=5s --retries=6 --start-period=25s \
  CMD wget -qO- http://localhost:8761/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
