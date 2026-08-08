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

# The platform routes to whatever we listen on; 8080 is Keycloak's default.
EXPOSE 8080

ENTRYPOINT ["/opt/keycloak/docker-entrypoint.sh"]
CMD ["start", "--import-realm", "--optimized"]
