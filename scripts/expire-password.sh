#!/usr/bin/env bash
# Ages or un-ages a user's password, for demoing forceExpiredPasswordChange without waiting.
#
#   ./scripts/expire-password.sh monti [days]   # expire (default 10 days old)
#   ./scripts/expire-password.sh monti --clear  # undo
#
# TWO non-obvious things, both learned the hard way:
#
#  1. created_date is written straight to Postgres, which bypasses Keycloak's Infinispan
#     user cache. Without the restart the stale cached credential is used, the login
#     succeeds, and the policy looks broken when it is not.
#
#  2. When expiry fires, Keycloak PERSISTS UPDATE_PASSWORD as a required action on the user.
#     Resetting created_date alone does NOT undo it — the action survives until the user
#     completes it or an admin removes it. --clear does both halves.
set -euo pipefail
cd "$(dirname "$0")/.."

U="${1:?usage: expire-password.sh <username> [days|--clear]}"
ARG="${2:-10}"
KC=http://localhost:8081
P=$(grep '^KC_ADMIN_PASSWORD=' .env | cut -d= -f2-)

kc_restart() {
  docker restart karaka-keycloak >/dev/null
  printf '   waiting for keycloak '
  for _ in $(seq 1 60); do
    curl -fsS -m 4 -o /dev/null "$KC/realms/karaka/.well-known/openid-configuration" 2>/dev/null \
      && { echo ' ready'; return 0; }
    printf '.'; sleep 3
  done
  echo ' TIMEOUT' >&2; return 1
}

if [[ "$ARG" == "--clear" ]]; then
  docker exec karaka-db psql -U keycloak -d keycloak -q -c \
    "update credential set created_date=(extract(epoch from now())*1000)::bigint
     where type='password' and user_id=(select id from user_entity where username='$U')"
  T=$(curl -fsS -m 20 -X POST "$KC/realms/master/protocol/openid-connect/token" \
       -d "client_id=admin-cli&username=admin&password=$P&grant_type=password" | jq -r .access_token)
  ID=$(curl -fsS -m 20 "$KC/admin/realms/karaka/users?username=$U&exact=true" -H "Authorization: Bearer $T" | jq -r '.[0].id')
  curl -fsS -m 20 "$KC/admin/realms/karaka/users/$ID" -H "Authorization: Bearer $T" \
    | jq '.requiredActions = []' > /tmp/exp-$$.json
  curl -fsS -m 20 -X PUT "$KC/admin/realms/karaka/users/$ID" -H "Authorization: Bearer $T" \
    -H 'Content-Type: application/json' --data @/tmp/exp-$$.json -o /dev/null
  rm -f /tmp/exp-$$.json
  kc_restart
  echo "$U: password age reset and UPDATE_PASSWORD cleared"
else
  docker exec karaka-db psql -U keycloak -d keycloak -q -c \
    "update credential set created_date=(extract(epoch from now() - interval '$ARG days')*1000)::bigint
     where type='password' and user_id=(select id from user_entity where username='$U')"
  kc_restart
  echo "$U: password is now $ARG days old — next login forces a change"
fi
