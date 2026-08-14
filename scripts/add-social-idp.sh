#!/usr/bin/env bash
#
# Adds or updates a social identity provider on the karaka realm.
#
# Replaces the earlier add-google-idp.sh. The Admin API shape is identical for every social
# provider — only providerId, the scope string and a couple of per-provider flags differ — so
# one script with a lookup table beats one script per provider. That decision was deferred
# until a second provider actually existed, which is now.
#
# WHY THE ADMIN API AND NOT THE REALM TEMPLATE
# `--import-realm` is IGNORE_EXISTING: it only ever applies to an empty database. A realm that
# already exists — the normal case locally, and always the case on a deployment — can never
# receive template changes. Putting providers in the template as well would create a second
# source of truth that is silently inapplicable most of the time.
#
# Usage:
#   ./scripts/add-social-idp.sh google
#   ./scripts/add-social-idp.sh twitter --if-configured   # no-op when credentials are absent
#   ./scripts/add-social-idp.sh github  --remove
#
# Credentials resolve from the environment first, then the secrets file for the target
# (.env for localhost, .azure-secrets otherwise), as:
#   <ALIAS>_CLIENT_ID   <ALIAS>_CLIENT_SECRET      e.g. TWITTER_CLIENT_ID
# Google additionally honours GOOGLE_HOSTED_DOMAIN.
set -euo pipefail
cd "$(dirname "$0")/.."

ALIAS="${1:-}"
[[ -n "$ALIAS" ]] || { echo "usage: add-social-idp.sh <alias> [--if-configured|--remove]" >&2; exit 2; }
shift

IF_CONFIGURED=0; REMOVE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --if-configured) IF_CONFIGURED=1; shift ;;
    --remove) REMOVE=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

KC="${KC:-https://auth.karakaa.com}"
REALM="${REALM:-karaka}"

# ---------------------------------------------------------------------------------------
# Per-provider differences. Everything not listed here is identical across providers.
#
# trustEmail says "this provider verified the address, so skip VERIFY_EMAIL". True only for
# providers that actually do verify, and only meaningful for ones that send an email at all.
# Setting it for a provider that does not verify is an account-takeover path, because email
# is the account-linking key in this realm.
# ---------------------------------------------------------------------------------------
#
# SYNC_MODE is the one that bites. FORCE re-imports the profile from the provider on EVERY
# login, which is right only when the provider is AUTHORITATIVE for those fields. For a
# provider that sends no email, FORCE overwrites whatever the user typed on the
# update-profile page back to empty — so they are asked for it again at every single login,
# forever, and the account keeps email=NULL. Observed exactly that with Twitter.
# IMPORT copies the profile once, at first login, and never touches it again.
case "$ALIAS" in
  google)
    # Google is authoritative for name and a verified email; keeping them in sync is correct.
    PROVIDER_ID=google;    SCOPE="openid profile email"; TRUST_EMAIL=true;  SYNC_MODE=FORCE ;;
  microsoft)
    PROVIDER_ID=microsoft; SCOPE="openid profile email"; TRUST_EMAIL=true;  SYNC_MODE=FORCE ;;
  github)
    # Returns an email only when the account has a public one, so it is not authoritative:
    # IMPORT, or a user who supplies their own address loses it on the next login.
    PROVIDER_ID=github;    SCOPE="user:email";           TRUST_EMAIL=false; SYNC_MODE=IMPORT ;;
  twitter)
    # OAuth 1.0a, and it supplies NO email at all: X gates that behind elevated "Request
    # email from users" permission, which Keycloak's twitter provider does not use. Every new
    # Twitter user must therefore type an email on the update-profile page, and IMPORT is
    # what lets that value survive.
    PROVIDER_ID=twitter;   SCOPE="";                     TRUST_EMAIL=false; SYNC_MODE=IMPORT ;;
  gitlab|bitbucket|linkedin-openid-connect|paypal|stackoverflow|openshift-v4)
    PROVIDER_ID="$ALIAS";  SCOPE="";                     TRUST_EMAIL=false; SYNC_MODE=IMPORT ;;
  *)
    echo "unknown alias '$ALIAS'. Add it to the case block with its providerId first." >&2
    exit 2 ;;
esac

if [[ -z "${SECRETS_FILE:-}" ]]; then
  if [[ "$KC" == *localhost* || "$KC" == *127.0.0.1* ]]; then SECRETS_FILE=.env
  else SECRETS_FILE=.azure-secrets
  fi
fi
# grep rather than source: sourcing executes arbitrary file content and clobbers caller vars.
# '|| true' is load-bearing — grep exits 1 for an absent key, and under `set -euo pipefail`
# that aborts the caller mid-assignment with no message at all.
from_file() {
  [[ -f "$SECRETS_FILE" ]] || return 0
  grep -m1 "^$1=" "$SECRETS_FILE" 2>/dev/null | cut -d= -f2- || true
}

UPPER=$(printf '%s' "$ALIAS" | tr '[:lower:]-' '[:upper:]_')
# Indirect expansion so TWITTER_CLIENT_ID in the ENVIRONMENT is honoured, not just in the
# secrets file. Without ${!var} this only ever read a generic CLIENT_ID, so the documented
# per-provider variables silently did nothing.
ID_VAR="${UPPER}_CLIENT_ID"
SECRET_VAR="${UPPER}_CLIENT_SECRET"
CLIENT_ID="${CLIENT_ID:-${!ID_VAR:-$(from_file "$ID_VAR")}}"
CLIENT_SECRET="${CLIENT_SECRET:-${!SECRET_VAR:-$(from_file "$SECRET_VAR")}}"
# ':-' would treat a deliberately empty value as unset; '-' preserves "unrestricted".
HOSTED_DOMAIN="${HOSTED_DOMAIN-$(from_file GOOGLE_HOSTED_DOMAIN)}"

KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-$(from_file KC_ADMIN_PASSWORD)}"
[[ -n "$KC_ADMIN_PASSWORD" ]] || { echo "no KC_ADMIN_PASSWORD in $SECRETS_FILE or environment" >&2; exit 1; }

token() {
  curl -fsS -m 30 -X POST "$KC/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli&username=admin&password=$KC_ADMIN_PASSWORD&grant_type=password" \
    | jq -r .access_token
}

if (( REMOVE )); then
  T=$(token)
  curl -sS -m 30 -X DELETE "$KC/admin/realms/$REALM/identity-provider/instances/$ALIAS" \
    -H "Authorization: Bearer $T" -w "══ $ALIAS removed -> %{http_code}\n" -o /dev/null
  exit 0
fi

if [[ -z "$CLIENT_ID" || -z "$CLIENT_SECRET" ]]; then
  # Every provider is a FEATURE, not a prerequisite: absent credentials must not fail a
  # bring-up, or one unconfigured provider takes the whole stack down.
  if (( IF_CONFIGURED )); then
    echo "══ $ALIAS sign-in: not configured (no ${UPPER}_CLIENT_ID/SECRET in $SECRETS_FILE) — skipping"
    exit 0
  fi
  echo "${UPPER}_CLIENT_ID and ${UPPER}_CLIENT_SECRET must be set, or present in $SECRETS_FILE" >&2
  exit 1
fi

T=$(token); [[ -n "$T" && "$T" != null ]] || { echo "could not get an admin token" >&2; exit 1; }
A=(-H "Authorization: Bearer $T"); J=(-H 'Content-Type: application/json')
echo "══ $ALIAS -> $KC (realm $REALM), inputs from $SECRETS_FILE"

BODY=$(jq -n \
  --arg alias "$ALIAS" --arg providerId "$PROVIDER_ID" \
  --arg cid "$CLIENT_ID" --arg sec "$CLIENT_SECRET" \
  --arg scope "$SCOPE" --arg hd "$HOSTED_DOMAIN" --arg sync "$SYNC_MODE" \
  --argjson trust "$TRUST_EMAIL" '
  {
    alias: $alias, providerId: $providerId, enabled: true,
    trustEmail: $trust, storeToken: false, linkOnly: false,
    firstBrokerLoginFlowAlias: "first broker login",
    config: ({ clientId: $cid, clientSecret: $sec, syncMode: $sync }
             + (if $scope == "" then {} else {defaultScope: $scope} end)
             + (if $providerId == "google" and $hd != "" then {hostedDomain: $hd} else {} end))
  }')

if curl -fsS -m 30 "$KC/admin/realms/$REALM/identity-provider/instances/$ALIAS" "${A[@]}" -o /dev/null 2>/dev/null; then
  curl -fsS -m 30 -X PUT "$KC/admin/realms/$REALM/identity-provider/instances/$ALIAS" \
    "${A[@]}" "${J[@]}" --data "$BODY" -w '   updated -> %{http_code}\n' -o /dev/null
else
  curl -fsS -m 30 -X POST "$KC/admin/realms/$REALM/identity-provider/instances" \
    "${A[@]}" "${J[@]}" --data "$BODY" -w '   created -> %{http_code}\n' -o /dev/null
fi

# Default 'first broker login' proves ownership of a pre-existing local account with the same
# email either by emailed link or by password. Disabling the email execution forces the
# password path, which is the only one that works when the realm has no SMTP. Idempotent, so
# it is safe to repeat for each provider.
EXEC=$(curl -fsS -m 30 "$KC/admin/realms/$REALM/authentication/flows/first%20broker%20login/executions" "${A[@]}" \
       | jq -c '.[] | select(.displayName|test("Verify Existing Account by Email";"i"))' || true)
if [[ -n "$EXEC" ]]; then
  jq -c '.requirement = "DISABLED"' <<<"$EXEC" \
    | curl -fsS -m 30 -X PUT "$KC/admin/realms/$REALM/authentication/flows/first%20broker%20login/executions" \
        "${A[@]}" "${J[@]}" --data @- -o /dev/null
fi

curl -fsS -m 30 "$KC/admin/realms/$REALM/identity-provider/instances/$ALIAS" "${A[@]}" | jq -r '
  "   providerId    \(.providerId)
   enabled       \(.enabled)
   trustEmail    \(.trustEmail)
   storeToken    \(.storeToken)
   scope         \(.config.defaultScope // "(provider default)")
   hostedDomain  \(.config.hostedDomain // "unrestricted")"'

if [[ "$TRUST_EMAIL" == false ]]; then
  cat <<EOF
   NOTE: trustEmail=false, so a new $ALIAS user must supply an email and verify it.
         That needs SMTP on the realm (scripts/set-smtp.sh) or they will be stranded.
EOF
fi
