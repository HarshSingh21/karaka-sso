#!/usr/bin/env bash
#
# Renders the realm template, then hands off to Keycloak.
#
# The realm file must contain no credentials in git, but Keycloak reads it from disk
# at import time — so the substitution has to happen inside the container, after the
# environment is available and before kc.sh starts.
#
# sed rather than envsubst: the Keycloak image is ubi9-micro based and ships sh, bash
# and sed but NOT envsubst or awk. Verified against quay.io/keycloak/keycloak:26.3.
set -euo pipefail

TEMPLATE=/opt/keycloak/realm-template/karaka-realm.template.json
IMPORT_DIR=/opt/keycloak/data/import
OUTPUT="$IMPORT_DIR/karaka-realm.json"

# Fail before Keycloak starts rather than importing a realm with a literal
# "${KARAKA_WEB_SECRET}" as the client secret — which would start cleanly and then
# reject every token exchange with invalid_client.
: "${KARAKA_WEB_SECRET:?must be set}"
: "${KARAKA_API_SECRET:?must be set}"
: "${DEMO_USER_PASSWORD:?must be set}"
: "${APP_BASE_URL:?must be set, e.g. https://karaka.onrender.com}"

if [[ -f "$TEMPLATE" ]]; then
  mkdir -p "$IMPORT_DIR"
  # '|' as the delimiter: base64 secrets contain +/= but never | or &, so neither the
  # delimiter nor sed's backreference character can appear in a replacement. Generate
  # secrets with `openssl rand -base64 32` and this holds.
  sed -e "s|\${KARAKA_WEB_SECRET}|${KARAKA_WEB_SECRET}|g" \
      -e "s|\${KARAKA_API_SECRET}|${KARAKA_API_SECRET}|g" \
      -e "s|\${DEMO_USER_PASSWORD}|${DEMO_USER_PASSWORD}|g" \
      -e "s|\${APP_BASE_URL}|${APP_BASE_URL}|g" \
      "$TEMPLATE" > "$OUTPUT"

  if grep -q '\${' "$OUTPUT"; then
    echo "FATAL: unsubstituted placeholders remain in the realm file:" >&2
    grep -o '\${[A-Z_]*}' "$OUTPUT" | sort -u >&2
    exit 1
  fi
  echo "Realm rendered to $OUTPUT"
fi

exec /opt/keycloak/bin/kc.sh "$@"
