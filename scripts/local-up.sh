#!/usr/bin/env bash
#
# Brings the whole local stack up, deterministically and idempotently.
#
# This is a composition root: its only job is ORDERING. Every step it performs is delegated
# to the script that owns it (render-realm.sh, docker compose, add-social-idp.sh), so there
# is exactly one implementation of each concern and this file adds no behaviour of its own.
#
#   ./scripts/local-up.sh            # converge the running stack
#   ./scripts/local-up.sh --fresh    # destroy the Keycloak database first, rebuild from .env
#   ./scripts/local-up.sh --build    # rebuild the service jar before starting
#
# WHY ORDER MATTERS — each of these is a failure someone has already hit:
#   1. Postgres before Keycloak — Keycloak's Liquibase migration aborts if the database is
#      not accepting connections, and the container exits rather than retrying.
#   2. Keycloak before the service — Spring resolves `issuer-uri` EAGERLY at boot, so a
#      service started first dies on discovery instead of waiting.
#   3. Realm import before provider reconciliation — the Admin API needs the realm to exist.
#
# --fresh is the only way realm-template changes take effect: `--import-realm` is
# IGNORE_EXISTING, so an existing database silently ignores them.
set -euo pipefail
cd "$(dirname "$0")/.."

FRESH=0; BUILD=0
for a in "$@"; do
  case "$a" in
    --fresh) FRESH=1 ;;
    --build) BUILD=1 ;;
    -h|--help) sed -n '2,20p' "$0" | sed 's/^#\s\?//'; exit 0 ;;
    *) echo "unknown option: $a (see --help)" >&2; exit 2 ;;
  esac
done

KC=http://localhost:8081
APP=http://localhost:8080
PIDFILE=.local-service.pid

# Fail before doing any work, not halfway through it.
[[ -f .env ]] || { echo "No .env — copy .env.example and fill it in" >&2; exit 1; }
command -v docker >/dev/null || { echo "docker is not on PATH" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "docker is not running" >&2; exit 1; }

# shellcheck disable=SC1091
set -a; source .env; set +a
: "${KEYCLOAK_CLIENT_SECRET:?set it in .env}"

# ---------------------------------------------------------------------------
# 1. Stop whatever is already running, so this script converges rather than
#    stacking a second service on a port that is already taken.
# ---------------------------------------------------------------------------
echo "══ 1. reset local service"
if [[ -f "$PIDFILE" ]] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  kill "$(cat "$PIDFILE")" 2>/dev/null || true
  echo "   stopped pid $(cat "$PIDFILE")"
fi
rm -f "$PIDFILE"
# A jar started outside this script (or before it existed) still holds the port.
for pid in $(lsof -nP -iTCP:8080 -sTCP:LISTEN -t 2>/dev/null || true); do
  kill "$pid" 2>/dev/null && echo "   stopped stray listener $pid" || true
done

# ---------------------------------------------------------------------------
# 2. Realm file. Rendered from the template + .env; the output is gitignored,
#    so no credential is ever committed.
# ---------------------------------------------------------------------------
echo "══ 2. render realm"
./scripts/render-realm.sh | sed 's/^/   /'

# ---------------------------------------------------------------------------
# 3. Keycloak + Postgres. compose gates Keycloak on the db healthcheck.
# ---------------------------------------------------------------------------
if (( FRESH )); then
  echo "══ 3. destroying the Keycloak database (--fresh)"
  docker compose down -v >/dev/null 2>&1 || true
  echo "   volume dropped — realm will re-import, existing users are gone"
else
  echo "══ 3. keycloak + postgres"
fi
docker compose up -d >/dev/null 2>&1
printf "   waiting for the realm "
for _ in $(seq 1 90); do
  curl -fsS -m 5 -o /dev/null "$KC/realms/karaka/.well-known/openid-configuration" 2>/dev/null \
    && { echo " ready"; break; }
  printf "."; sleep 3
done
curl -fsS -m 5 -o /dev/null "$KC/realms/karaka/.well-known/openid-configuration" \
  || { echo; echo "   Keycloak never became ready — docker compose logs keycloak" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 4. Optional realm configuration, reconciled through the Admin API.
#    --if-configured keeps each of these a FEATURE rather than a PREREQUISITE:
#    absent credentials skip the step and say so, they do not fail the bring-up.
#    SMTP first — "Forgot Password?" is inert without it.
# ---------------------------------------------------------------------------
echo "══ 4. optional realm config"
KC="$KC" ./scripts/set-smtp.sh --if-configured 2>&1 | sed 's/^/   /'
# Every provider Karaka knows how to configure. Each is skipped unless its credentials are
# present, so this list can grow without making any of them a prerequisite.
for provider in google twitter github microsoft; do
  KC="$KC" ./scripts/add-social-idp.sh "$provider" --if-configured 2>&1 | sed 's/^/   /'
done

# ---------------------------------------------------------------------------
# 5. The service.
# ---------------------------------------------------------------------------
echo "══ 5. service"
if (( BUILD )) || ! ls service/target/karaka-service-*.jar >/dev/null 2>&1; then
  echo "   building"
  (cd service && mvn -q -DskipTests package) || { echo "   build failed" >&2; exit 1; }
fi
JAR=$(ls -t service/target/karaka-service-*.jar | head -1)
KEYCLOAK_ISSUER_URI="$KC/realms/karaka" \
KEYCLOAK_CLIENT_SECRET="$KEYCLOAK_CLIENT_SECRET" \
KARAKA_API_SECRET="${KARAKA_API_SECRET:-}" \
KARAKA_COOKIE_SECURE=false \
  nohup java -jar "$JAR" > /tmp/karaka-local.log 2>&1 &
echo $! > "$PIDFILE"
printf "   pid %s, waiting for health " "$(cat "$PIDFILE")"
for _ in $(seq 1 60); do
  [[ "$(curl -sS -m 4 -o /dev/null -w '%{http_code}' "$APP/actuator/health" 2>/dev/null)" == 200 ]] \
    && { echo " up"; break; }
  printf "."; sleep 2
done

# ---------------------------------------------------------------------------
# 6. Report observed state. Printing what was measured, not what was intended.
# ---------------------------------------------------------------------------
echo "══ 6. state"
printf "   app       %s  (health %s)\n" "$APP" \
  "$(curl -sS -m 8 -o /dev/null -w '%{http_code}' "$APP/actuator/health")"
printf "   keycloak  %s/admin  (%s / %s)\n" "$KC" "${KC_ADMIN_USER:-admin}" "${KC_ADMIN_PASSWORD:-see .env}"
printf "   issuer    %s\n" "$(curl -sS -m 8 "$KC/realms/karaka/.well-known/openid-configuration" | sed -n 's/.*"issuer":"\([^"]*\)".*/\1/p')"
printf "   demo user ankit / %s\n" "$DEMO_USER_PASSWORD"
GOOGLE_BTN=$(curl -sSL -m 20 "$APP/picker" 2>/dev/null | grep -c 'social-google' || true)
printf "   google    %s\n" "$([[ "$GOOGLE_BTN" -gt 0 ]] && echo 'sign-in button present' || echo 'not configured')"
echo
echo "   logs: tail -f /tmp/karaka-local.log   |   teardown: docker compose down && kill \$(cat $PIDFILE)"
