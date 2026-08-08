#!/usr/bin/env bash
#
# Renders keycloak/realm/karaka-realm.template.json for LOCAL development.
#
# The template is the only realm file in git; the rendered output is gitignored. Run
# this before `docker compose up` — Keycloak imports whatever .json it finds in that
# directory, and without this there is nothing to import.
#
# In a deployment the same substitution happens inside the container at start, via
# keycloak/docker-entrypoint.sh. Two places, one format, so a placeholder added here
# has to be added there too — the entrypoint fails loudly if one is left unsubstituted.
set -euo pipefail

cd "$(dirname "$0")/.."

TEMPLATE=keycloak/realm-template/karaka-realm.template.json
OUTPUT=keycloak/realm/karaka-realm.json

[[ -f .env ]] || { echo "No .env — copy .env.example and fill it in first" >&2; exit 1; }
# shellcheck disable=SC1091
set -a; source .env; set +a

: "${KARAKA_WEB_SECRET:?set it in .env}"
: "${KARAKA_API_SECRET:?set it in .env}"
: "${DEMO_USER_PASSWORD:?set it in .env}"
: "${APP_BASE_URL:=http://localhost:8080}"

sed -e "s|\${KARAKA_WEB_SECRET}|${KARAKA_WEB_SECRET}|g" \
    -e "s|\${KARAKA_API_SECRET}|${KARAKA_API_SECRET}|g" \
    -e "s|\${DEMO_USER_PASSWORD}|${DEMO_USER_PASSWORD}|g" \
    -e "s|\${APP_BASE_URL}|${APP_BASE_URL}|g" \
    "$TEMPLATE" > "$OUTPUT"

if grep -q '\${' "$OUTPUT"; then
  echo "FATAL: unsubstituted placeholders remain:" >&2
  grep -o '\${[A-Z_]*}' "$OUTPUT" | sort -u >&2
  rm -f "$OUTPUT"
  exit 1
fi

echo "Rendered $OUTPUT (gitignored)"
echo "Realm changes need: docker compose down -v && docker compose up -d"
