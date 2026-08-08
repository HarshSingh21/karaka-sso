# Karaka service — build and runtime.
#
# Build context is the repository root, so this can be pointed at from a platform
# config without a separate context directory.

# ---- build -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so a source-only change does not refetch
# the whole tree on every deploy.
COPY service/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline

COPY service/src ./src
RUN mvn -B -DskipTests package \
    && cp target/karaka-service-*.jar /build/app.jar

# ---- runtime ---------------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl is needed by the entrypoint's readiness check. A TCP probe would not do:
# behind a platform router, the port answers even while the target is asleep, so the
# check has to be an actual HTTP request to the discovery document.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/app.jar ./app.jar
COPY service/docker-entrypoint.sh ./docker-entrypoint.sh
RUN chmod +x ./docker-entrypoint.sh

# Run unprivileged.
RUN useradd --system --uid 10001 karaka && chown -R karaka /app
USER karaka

# Container memory is small on free tiers; let the JVM see the cgroup limit rather
# than guessing from host RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

EXPOSE 8080
ENTRYPOINT ["./docker-entrypoint.sh"]
