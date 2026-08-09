#!/usr/bin/env bash
#
# Deploys the whole Karaka stack to Azure Container Apps, end to end.
#
#   Postgres  Azure Database for PostgreSQL Flexible Server (B1ms burstable)
#   Keycloak  Container App, 2 GB — the reason this works where Render's free tier
#             did not: 512 MB cannot start Keycloak, proven twice
#   Service   Container App, 1 GB
#
# Prerequisites, all yours:
#   1. An Azure account (new ones get US$200 of credit for 30 days)
#   2. az CLI installed and `az login` completed
#
# Usage:
#   ./scripts/azure-deploy.sh                 # deploy or update
#   ./scripts/azure-deploy.sh --destroy       # delete everything
#
# Idempotent: safe to re-run. Every resource lives in one resource group, so
# --destroy is a single delete.
set -euo pipefail

RG="${AZ_RG:-karaka}"
LOC="${AZ_LOCATION:-centralindia}"
ENV_NAME="${AZ_ENV:-karaka-env}"
ACR="${AZ_ACR:-karaka$RANDOM}"          # must be globally unique, alphanumeric only
PG="${AZ_PG:-karaka-pg-$RANDOM}"        # must be globally unique
PG_DB=keycloak
PG_USER=karaka
KC_APP=karaka-keycloak
SVC_APP=karaka

if [[ "${1:-}" == "--destroy" ]]; then
  echo "Deleting resource group $RG and everything in it."
  az group delete -n "$RG" --yes --no-wait
  echo "Deletion started. It continues in the background."
  exit 0
fi

# ---------------------------------------------------------------------------
# 0. Preflight
# ---------------------------------------------------------------------------
command -v az >/dev/null || { echo "az CLI not installed: brew install azure-cli" >&2; exit 1; }
az account show >/dev/null 2>&1 || { echo "Not logged in: run 'az login'" >&2; exit 1; }
echo "══ subscription: $(az account show --query name -o tsv)"

# Registering these up front. On a new subscription they are not registered, and the
# failure surfaces much later as an opaque "resource type not supported in location".
echo "══ registering providers (one-time, may take a minute)"
for p in Microsoft.App Microsoft.OperationalInsights Microsoft.ContainerRegistry Microsoft.DBforPostgreSQL; do
  az provider register -n "$p" --wait
done
az extension add --name containerapp --upgrade --only-show-errors >/dev/null 2>&1 || true

# ---------------------------------------------------------------------------
# 1. Secrets, generated locally and never committed
# ---------------------------------------------------------------------------
gen() { openssl rand -base64 "${1:-32}" | tr -d '\n|&+/='; }
SECRETS_FILE=".azure-secrets"
if [[ -f "$SECRETS_FILE" ]]; then
  echo "══ reusing $SECRETS_FILE"
else
  WEB=$(gen 32)
  cat > "$SECRETS_FILE" <<EOF
# Generated $(date -u +%Y-%m-%dT%H:%M:%SZ). Gitignored. Keep it: re-running without
# this file would mint new secrets and invalidate the deployed realm.
PG_PASSWORD=$(gen 24)
KARAKA_WEB_SECRET=$WEB
KEYCLOAK_CLIENT_SECRET=$WEB
KARAKA_API_SECRET=$(gen 32)
DEMO_USER_PASSWORD=$(gen 18)
KC_ADMIN_PASSWORD=$(gen 24)
EOF
  chmod 600 "$SECRETS_FILE"
  echo "══ wrote $SECRETS_FILE (mode 600)"
fi
# shellcheck disable=SC1090
set -a; source "$SECRETS_FILE"; set +a

# ---------------------------------------------------------------------------
# 2. Resource group, registry
# ---------------------------------------------------------------------------
echo "══ resource group"
az group create -n "$RG" -l "$LOC" -o none

echo "══ container registry"
if ! az acr show -n "$ACR" -g "$RG" >/dev/null 2>&1; then
  # A pre-existing ACR from an earlier run has a different random suffix; find it.
  EXISTING=$(az acr list -g "$RG" --query "[0].name" -o tsv 2>/dev/null || true)
  if [[ -n "$EXISTING" ]]; then ACR="$EXISTING"; echo "   reusing $ACR"
  else az acr create -n "$ACR" -g "$RG" --sku Basic --admin-enabled true -o none; echo "   created $ACR"; fi
fi

ACR_SERVER=$(az acr show -n "$ACR" -g "$RG" --query loginServer -o tsv)

# Built locally and pushed, NOT with `az acr build`. Azure blocks ACR Tasks on trial
# subscriptions — server-side builds fail with TasksOperationsNotAllowed — so the
# server-side path is unavailable regardless of how convenient it would be.
#
# --platform linux/amd64 is mandatory: Container Apps runs amd64, and an arm64 image
# pushed from an Apple Silicon machine fails at runtime with an exec format error
# rather than at build or push time.
echo "══ building and pushing images (amd64)"
az acr login -n "$ACR" -o none
docker buildx create --use --name karaka-builder >/dev/null 2>&1 || docker buildx use karaka-builder

# Keycloak cross-builds directly: its only heavy step is kc.sh build, which is tolerable
# under emulation.
docker buildx build --platform linux/amd64 -f keycloak/Dockerfile \
  -t "$ACR_SERVER/karaka-keycloak:latest" --push . >/dev/null

# The service does NOT cross-build its Maven stage. Java bytecode is
# architecture-independent, so the jar is built natively at full speed and only the thin
# runtime layer is cross-built. Emulating Maven would cost many minutes for nothing.
echo "   building the jar natively first"
# `clean` is not optional here. Stale classes in target/ from an earlier build break
# the Spring Boot repackage step: macOS had left "KarakaApplication 2.class" alongside
# the real one, and the plugin refused with "Unable to find a single main class". The
# COPY glob in Dockerfile.prebuilt would be similarly ambiguous with two jars present.
docker run --rm -v "$PWD/service":/app -v "$HOME/.m2":/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-25 mvn -B -q -DskipTests clean package
docker buildx build --platform linux/amd64 -f service/Dockerfile.prebuilt \
  -t "$ACR_SERVER/karaka-service:latest" --push . >/dev/null
ACR_USER=$(az acr credential show -n "$ACR" --query username -o tsv)
ACR_PASS=$(az acr credential show -n "$ACR" --query 'passwords[0].value' -o tsv)

# ---------------------------------------------------------------------------
# 3. Postgres
# ---------------------------------------------------------------------------
echo "══ postgres"
if ! az postgres flexible-server show -g "$RG" -n "$PG" >/dev/null 2>&1; then
  EXISTING_PG=$(az postgres flexible-server list -g "$RG" --query "[0].name" -o tsv 2>/dev/null || true)
  if [[ -n "$EXISTING_PG" ]]; then PG="$EXISTING_PG"; echo "   reusing $PG"
  else
    az postgres flexible-server create -g "$RG" -n "$PG" -l "$LOC" \
      --admin-user "$PG_USER" --admin-password "$PG_PASSWORD" \
      --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 16 \
      --public-access 0.0.0.0 --yes -o none
    echo "   created $PG"
  fi
fi
# The flag is --name, not -d. And the failure is NOT suppressed: an earlier version
# had `2>/dev/null || true` to make this idempotent, which instead hid the wrong flag
# entirely — az printed its help text, exited 0, and the database was never created.
# Keycloak then failed much later with FATAL: database "keycloak" does not exist.
#
# Idempotency is achieved by checking first rather than by discarding errors.
if ! az postgres flexible-server db list -g "$RG" -s "$PG" --query "[].name" -o tsv | grep -qx "$PG_DB"; then
  az postgres flexible-server db create -g "$RG" -s "$PG" --name "$PG_DB" -o none
  echo "   created database $PG_DB"
else
  echo "   database $PG_DB already present"
fi
PG_HOST="$PG.postgres.database.azure.com"
# JDBC form, not the psql URI: the driver cannot parse user:password@host, and
# sslmode=require is mandatory because Azure rejects unencrypted connections.
KC_DB_URL="jdbc:postgresql://$PG_HOST:5432/$PG_DB?sslmode=require"

# ---------------------------------------------------------------------------
# 4. Container Apps environment
# ---------------------------------------------------------------------------
echo "══ container apps environment"
az containerapp env show -n "$ENV_NAME" -g "$RG" >/dev/null 2>&1 || \
  az containerapp env create -n "$ENV_NAME" -g "$RG" -l "$LOC" -o none

# ---------------------------------------------------------------------------
# 5. Keycloak — pass one, without the hostname it cannot know yet
# ---------------------------------------------------------------------------
echo "══ keycloak (pass 1: create)"
if ! az containerapp show -n "$KC_APP" -g "$RG" >/dev/null 2>&1; then
  az containerapp create -n "$KC_APP" -g "$RG" --environment "$ENV_NAME" \
    --image "$ACR_SERVER/karaka-keycloak:latest" \
    --registry-server "$ACR_SERVER" --registry-username "$ACR_USER" --registry-password "$ACR_PASS" \
    --target-port 8080 --ingress external --transport http \
    --cpu 1.0 --memory 2.0Gi \
    --min-replicas 0 --max-replicas 1 \
    --secrets "db-password=$PG_PASSWORD" "web-secret=$KARAKA_WEB_SECRET" \
              "api-secret=$KARAKA_API_SECRET" "demo-password=$DEMO_USER_PASSWORD" \
              "kc-admin-password=$KC_ADMIN_PASSWORD" \
    --env-vars \
      "KC_DB_URL=$KC_DB_URL" "KC_DB_USERNAME=$PG_USER" "KC_DB_PASSWORD=secretref:db-password" \
      "KARAKA_WEB_SECRET=secretref:web-secret" "KARAKA_API_SECRET=secretref:api-secret" \
      "DEMO_USER_PASSWORD=secretref:demo-password" \
      "KC_BOOTSTRAP_ADMIN_USERNAME=admin" "KC_BOOTSTRAP_ADMIN_PASSWORD=secretref:kc-admin-password" \
      "APP_BASE_URL=https://placeholder.invalid" "KC_HOSTNAME=https://placeholder.invalid" \
    -o none
fi
KC_FQDN=$(az containerapp show -n "$KC_APP" -g "$RG" --query properties.configuration.ingress.fqdn -o tsv)
KC_URL="https://$KC_FQDN"

# ---------------------------------------------------------------------------
# 6. Service — created next so its URL is known before Keycloak's final update
# ---------------------------------------------------------------------------
echo "══ service (pass 1: create)"
if ! az containerapp show -n "$SVC_APP" -g "$RG" >/dev/null 2>&1; then
  az containerapp create -n "$SVC_APP" -g "$RG" --environment "$ENV_NAME" \
    --image "$ACR_SERVER/karaka-service:latest" \
    --registry-server "$ACR_SERVER" --registry-username "$ACR_USER" --registry-password "$ACR_PASS" \
    --target-port 8080 --ingress external --transport http \
    --cpu 0.5 --memory 1.0Gi \
    --min-replicas 0 --max-replicas 1 \
    --secrets "client-secret=$KEYCLOAK_CLIENT_SECRET" \
    --env-vars \
      "KEYCLOAK_ISSUER_URI=$KC_URL/realms/karaka" \
      "KEYCLOAK_CLIENT_SECRET=secretref:client-secret" \
      "KARAKA_COOKIE_SECURE=true" \
      "SERVER_FORWARD_HEADERS_STRATEGY=framework" \
    -o none
fi
SVC_FQDN=$(az containerapp show -n "$SVC_APP" -g "$RG" --query properties.configuration.ingress.fqdn -o tsv)
SVC_URL="https://$SVC_FQDN"

# ---------------------------------------------------------------------------
# 7. Pass two: the URLs only exist now
# ---------------------------------------------------------------------------
# This is the whole reason the script is two-pass. Keycloak needs its own public URL
# (KC_HOSTNAME) and the app's (APP_BASE_URL, which becomes the client's redirect URI in
# the realm), and neither is knowable until Azure has assigned the FQDNs. Getting
# APP_BASE_URL wrong is the subtle one: Keycloak then refuses the redirect AFTER the
# password is accepted, which reads like a broken login rather than a URL mismatch.
echo "══ keycloak (pass 2: real URLs)"
az containerapp update -n "$KC_APP" -g "$RG" \
  --set-env-vars "KC_HOSTNAME=$KC_URL" "APP_BASE_URL=$SVC_URL" -o none

echo "══ service (pass 2: issuer)"
az containerapp update -n "$SVC_APP" -g "$RG" \
  --set-env-vars "KEYCLOAK_ISSUER_URI=$KC_URL/realms/karaka" -o none

# ---------------------------------------------------------------------------
# 8. Wait, then report
# ---------------------------------------------------------------------------
echo "══ waiting for Keycloak (cold start on a scale-to-zero app is ~45s)"
for i in $(seq 1 40); do
  if curl -fsS -m 20 -o /dev/null "$KC_URL/realms/karaka/.well-known/openid-configuration"; then
    echo "   ready after ${i} attempts"; break
  fi
  printf '.'; sleep 10
done

cat <<EOF

══ Deployed

  App        $SVC_URL
  Keycloak   $KC_URL/admin

  Sign in as ankit / $DEMO_USER_PASSWORD
  Keycloak admin: admin / $KC_ADMIN_PASSWORD

  Secrets are in $SECRETS_FILE (gitignored). Keep it — re-running without it mints
  new secrets and invalidates the deployed realm.

  Cost: both apps scale to zero, so you pay nothing while idle. The always-free grant
  is ~180k vCPU-seconds and ~360k GiB-seconds per month, which is roughly 50 hours at
  Keycloak's 2 GB. Postgres B1ms is the part that bills continuously — it is covered by
  the 12-month free tier on a new account, and by credit after that.

  Tear everything down with:  ./scripts/azure-deploy.sh --destroy
EOF
