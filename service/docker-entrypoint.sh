#!/usr/bin/env bash
#
# Waits for Keycloak, then starts the service.
#
# Spring resolves the OIDC discovery document at startup when `issuer-uri` is set, so
# the process exits if Keycloak is unreachable. That is fine locally, where Keycloak is
# already up, but on a platform where both services sleep and cold-start independently
# it turns an ordinary cold start into a crash loop.
#
# The alternative — dropping `issuer-uri` and listing each endpoint explicitly — avoids
# the startup call but also stops Spring validating the ID token's `iss` claim against
# a known issuer. Waiting is the cheaper trade: it costs seconds at boot and keeps the
# validation.
set -euo pipefail

: "${KEYCLOAK_ISSUER_URI:?must be set, e.g. https://karaka-keycloak.onrender.com/realms/karaka}"

DISCOVERY="${KEYCLOAK_ISSUER_URI%/}/.well-known/openid-configuration"
ATTEMPTS="${KEYCLOAK_WAIT_ATTEMPTS:-60}"
INTERVAL="${KEYCLOAK_WAIT_INTERVAL:-5}"

echo "Waiting for Keycloak at $DISCOVERY"
for attempt in $(seq 1 "$ATTEMPTS"); do
  if curl -fsS -m 10 -o /dev/null "$DISCOVERY"; then
    echo "Keycloak is ready (attempt $attempt)"
    exec java $JAVA_OPTS -jar /app/app.jar
  fi
  # The first request also serves to wake a sleeping instance, so a slow first
  # attempt is expected rather than a fault.
  echo "  not ready yet (attempt $attempt/$ATTEMPTS); retrying in ${INTERVAL}s"
  sleep "$INTERVAL"
done

echo "FATAL: Keycloak did not become ready after $((ATTEMPTS * INTERVAL))s" >&2
exit 1
