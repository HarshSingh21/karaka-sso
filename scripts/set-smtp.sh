#!/usr/bin/env bash
#
# Configures the realm's SMTP server, which is what makes these work:
#   - the "Forgot Password?" link (resetPasswordAllowed)
#   - admin-initiated "Update Password" emails (Users -> Credentials -> Credential Reset)
#   - VERIFY_EMAIL, if ever enabled
#
# Without it the link renders and then throws:
#   FreeMarkerEmailTemplateProvider.sendPasswordReset -> EmailException
#
# WHY NOT IN THE REALM TEMPLATE
# Same reason as the identity provider: `--import-realm` is IGNORE_EXISTING, so template
# changes never reach an existing realm. Adding ${SMTP_*} placeholders would also make SMTP
# MANDATORY, because render-realm.sh aborts on any unsubstituted placeholder — turning an
# optional feature into a hard prerequisite. Optional realm config is reconciled through the
# Admin API; the template holds only what every deployment must have.
#
# Usage:
#   ./scripts/set-smtp.sh                  # reads SMTP_* from the secrets file
#   ./scripts/set-smtp.sh --if-configured  # no-op instead of error when unset
#   ./scripts/set-smtp.sh --test <email>   # configure, then send a real test message
#
# Inputs (environment first, then .env for localhost / .azure-secrets otherwise):
#   SMTP_HOST SMTP_PORT SMTP_FROM SMTP_USER SMTP_PASSWORD
#   SMTP_FROM_NAME  SMTP_STARTTLS(default true)  SMTP_SSL(default false)  SMTP_AUTH(default true)
set -euo pipefail
cd "$(dirname "$0")/.."

IF_CONFIGURED=0; TEST_TO=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --if-configured) IF_CONFIGURED=1; shift ;;
    --test) TEST_TO="${2:?--test needs an email address}"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

KC="${KC:-https://auth.karakaa.com}"
REALM="${REALM:-karaka}"

if [[ -z "${SECRETS_FILE:-}" ]]; then
  if [[ "$KC" == *localhost* || "$KC" == *127.0.0.1* ]]; then SECRETS_FILE=.env
  else SECRETS_FILE=.azure-secrets
  fi
fi
# grep rather than source: sourcing executes arbitrary file content and clobbers caller vars.
from_file() {
  [[ -f "$SECRETS_FILE" ]] || return 0
  # '|| true' is load-bearing — see the note in add-social-idp.sh: an absent key makes grep
  # exit 1, which under `set -euo pipefail` silently aborts the caller mid-assignment.
  grep -m1 "^$1=" "$SECRETS_FILE" 2>/dev/null | cut -d= -f2- || true
}

SMTP_HOST="${SMTP_HOST:-$(from_file SMTP_HOST)}"
SMTP_PORT="${SMTP_PORT:-$(from_file SMTP_PORT)}"
SMTP_FROM="${SMTP_FROM:-$(from_file SMTP_FROM)}"
SMTP_USER="${SMTP_USER:-$(from_file SMTP_USER)}"
SMTP_PASSWORD="${SMTP_PASSWORD:-$(from_file SMTP_PASSWORD)}"
SMTP_FROM_NAME="${SMTP_FROM_NAME:-$(from_file SMTP_FROM_NAME)}"
SMTP_STARTTLS="${SMTP_STARTTLS:-$(from_file SMTP_STARTTLS)}"
SMTP_SSL="${SMTP_SSL:-$(from_file SMTP_SSL)}"

if [[ -z "$SMTP_HOST" || -z "$SMTP_FROM" ]]; then
  if (( IF_CONFIGURED )); then
    echo "══ SMTP: not configured (no SMTP_HOST/SMTP_FROM in $SECRETS_FILE) — skipping"
    echo "   'Forgot Password?' will render but fail on submit until this is set."
    exit 0
  fi
  echo "SMTP_HOST and SMTP_FROM must be set, or present in $SECRETS_FILE" >&2
  exit 1
fi

: "${SMTP_PORT:=587}"
: "${SMTP_STARTTLS:=true}"
: "${SMTP_SSL:=false}"
: "${SMTP_FROM_NAME:=Karaka}"

KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-$(from_file KC_ADMIN_PASSWORD)}"
[[ -n "$KC_ADMIN_PASSWORD" ]] || { echo "no KC_ADMIN_PASSWORD in $SECRETS_FILE or environment" >&2; exit 1; }
echo "══ target $KC   inputs from $SECRETS_FILE"

T=$(curl -fsS -m 30 -X POST "$KC/realms/master/protocol/openid-connect/token" \
     -d "client_id=admin-cli&username=admin&password=$KC_ADMIN_PASSWORD&grant_type=password" | jq -r .access_token)
[[ -n "$T" && "$T" != null ]] || { echo "could not get an admin token" >&2; exit 1; }
A=(-H "Authorization: Bearer $T"); J=(-H 'Content-Type: application/json')

echo "══ 1. smtpServer on realm '$REALM'"
# Read-modify-write the whole realm: a partial PUT can blank unrelated settings.
# 'auth' is only declared when a username is present — Keycloak rejects auth=true with no user.
curl -fsS -m 30 "$KC/admin/realms/$REALM" "${A[@]}" \
  | jq --arg h "$SMTP_HOST" --arg p "$SMTP_PORT" --arg f "$SMTP_FROM" --arg fn "$SMTP_FROM_NAME" \
       --arg u "$SMTP_USER" --arg pw "$SMTP_PASSWORD" --arg tls "$SMTP_STARTTLS" --arg ssl "$SMTP_SSL" '
      .smtpServer = ({host:$h, port:$p, from:$f, fromDisplayName:$fn, starttls:$tls, ssl:$ssl}
                     + (if $u == "" then {auth:"false"} else {auth:"true", user:$u, password:$pw} end))' \
  > /tmp/smtp-$$.json
curl -fsS -m 30 -X PUT "$KC/admin/realms/$REALM" "${A[@]}" "${J[@]}" \
  --data @/tmp/smtp-$$.json -w '   PUT -> %{http_code}\n' -o /dev/null
rm -f /tmp/smtp-$$.json

echo "══ 2. verify (password is never echoed)"
curl -fsS -m 30 "$KC/admin/realms/$REALM" "${A[@]}" | jq -r '
  "   host      \(.smtpServer.host)
   port      \(.smtpServer.port)
   from      \(.smtpServer.from)
   auth      \(.smtpServer.auth)   user=\(.smtpServer.user // "-")
   starttls  \(.smtpServer.starttls)   ssl=\(.smtpServer.ssl)
   password  \(if (.smtpServer.password // "") == "" then "NOT SET" else "set" end)"'

if [[ -n "$TEST_TO" ]]; then
  echo "══ 3. sending a test message to $TEST_TO"
  # testSMTPConnection is the only way to find out whether the credentials actually work.
  # A 204 here means the message left Keycloak; anything else is a real misconfiguration.
  CFG=$(curl -fsS -m 30 "$KC/admin/realms/$REALM" "${A[@]}" | jq -c '.smtpServer')
  curl -sS -m 60 -X POST "$KC/admin/realms/$REALM/testSMTPConnection" "${A[@]}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "config=$CFG" -o /tmp/smtptest-$$.txt -w '   testSMTPConnection -> %{http_code}\n'
  [[ -s /tmp/smtptest-$$.txt ]] && head -c 400 /tmp/smtptest-$$.txt | sed 's/^/   /'
  rm -f /tmp/smtptest-$$.txt
  echo "   (204 = sent. Check the inbox; delivery is the mail provider's job from here.)"
fi
