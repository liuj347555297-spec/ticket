# syntax=docker/dockerfile:1
# The build and runtime bases are intentionally supplied by the approved internal registry as
# immutable digests.  Supplying a floating tag is rejected by devops/ci/verify-container-input.sh.
# Example: --build-arg SERVICEHUB_BUILD_IMAGE=registry.internal/maven@sha256:<digest>
ARG SERVICEHUB_BUILD_IMAGE
ARG SERVICEHUB_RUNTIME_IMAGE
FROM ${SERVICEHUB_BUILD_IMAGE} AS build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline
COPY backend/ ./
RUN mvn -B -DskipTests package

FROM ${SERVICEHUB_RUNTIME_IMAGE}
RUN addgroup -S servicehub && adduser -S -G servicehub -h /app servicehub \
    && apk add --no-cache wget \
    && mkdir -p /app /var/lib/servicehub/attachments /var/lib/servicehub/tmp \
    && chown -R servicehub:servicehub /app /var/lib/servicehub
WORKDIR /app
COPY --from=build --chown=servicehub:servicehub /workspace/backend/target/servicehub-backend-*.jar /app/servicehub-backend.jar
USER servicehub:servicehub
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/servicehub-backend.jar"]
