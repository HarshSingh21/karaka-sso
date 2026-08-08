# Keycloak for a hosted deployment.
#
# Differs from local development in three ways that matter:
#   - `start`, not `start-dev`: hostname checks and theme caching are enabled
#   - the realm file is rendered from a template at container start, so no secret
#     is committed
#   - proxy headers are trusted, because the platform terminates TLS in front of us
FROM quay.io/keycloak/keycloak:26.3

# Theme and realm template are baked in; the rendered realm is written at runtime.
COPY --chown=keycloak:root keycloak/themes/karaka /opt/keycloak/themes/karaka
COPY --chown=keycloak:root keycloak/realm-template/karaka-realm.template.json \
     /opt/keycloak/realm-template/karaka-realm.template.json
COPY --chown=keycloak:root keycloak/docker-entrypoint.sh /opt/keycloak/docker-entrypoint.sh

USER root
RUN chmod +x /opt/keycloak/docker-entrypoint.sh \
    && mkdir -p /opt/keycloak/data/import \
    && chown -R keycloak:root /opt/keycloak/data
USER keycloak

# Ahead-of-time build step. Without it Keycloak re-augments on every boot, which on a
# free tier that sleeps means paying that cost at every cold start.
RUN /opt/keycloak/bin/kc.sh build --db=postgres

# Runtime options only. KC_DB and KC_HEALTH_ENABLED must NOT appear here: they are
# BUILD-time options, already baked in by the kc.sh build above. With --optimized,
# Keycloak refuses to start if a build-time option is supplied at runtime and exits 2
# with "build time options have values that differ from what is persisted".
#
# Health is deliberately not enabled at all. It would have to be a build flag, and the
# Render health check uses the realm's discovery endpoint on the main port instead —
# which is a better probe anyway, since it proves the realm imported rather than only
# that the process is up. Keycloak's own /health lives on management port 9000, which
# a single-port platform does not expose.
ENV KC_HTTP_ENABLED=true \
    KC_PROXY_HEADERS=xforwarded \
    KC_HOSTNAME_STRICT=false

# Memory budget for a 512 MB container. Keycloak's own default is
# MaxRAMPercentage=70, which leaves too little for everything that is NOT heap —
# metaspace, code cache, thread stacks and Netty's direct buffers — and the kernel
# kills the process (exit 137) before Quarkus finishes starting.
#
# Explicit numbers rather than percentages, so the sum is auditable:
#   heap 256 + metaspace 112 + code cache 32 + native/threads ~60  =  ~460 of 512
ENV JAVA_OPTS_KC_HEAP="-Xms48m -Xmx256m"
# SerialGC: on 0.1 CPU the parallel collectors' extra threads cost more than they
# return. TieredStopAtLevel=1 keeps the JIT at C1, cutting both compile memory and
# startup time. Netty's default 16 MB buffer chunks are far too coarse here, so
# maxOrder drops them to 512 KB.
ENV JAVA_OPTS_APPEND="-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=32m -Dio.netty.allocator.maxOrder=5"

# A cold start on this instance is slow — a starved JVM plus first-boot schema
# creation. Render's default health-check grace is not generous enough.
ENV KC_HTTP_MANAGEMENT_PORT=9000

# The platform routes to whatever we listen on; 8080 is Keycloak's default.
EXPOSE 8080

ENTRYPOINT ["/opt/keycloak/docker-entrypoint.sh"]
CMD ["start", "--import-realm", "--optimized"]
