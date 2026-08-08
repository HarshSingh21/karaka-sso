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

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_HTTP_ENABLED=true \
    KC_PROXY_HEADERS=xforwarded \
    KC_HOSTNAME_STRICT=false

# The platform routes to whatever we listen on; 8080 is Keycloak's default.
EXPOSE 8080

ENTRYPOINT ["/opt/keycloak/docker-entrypoint.sh"]
CMD ["start", "--import-realm", "--optimized"]
