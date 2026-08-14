#!/usr/bin/env bash
# Brings the stopped Karaka stack back up on karakaa.com.
#
# Counterpart to scripts/azure-stop.sh. Order matters: Postgres must be accepting
# connections before Keycloak starts, or Keycloak's Liquibase migration fails and the
# revision goes ActivationFailed. The service then needs Keycloak's discovery document
# at boot, because Spring resolves issuer-uri eagerly.
set -euo pipefail
RG=karaka; PG=karaka-pg-578
KC=https://auth.karakaa.com; APP=https://karakaa.com

echo "══ 1. Postgres"
[[ "$(az postgres flexible-server show -g $RG -n $PG --query state -o tsv)" == Ready ]] \
  || az postgres flexible-server start -g $RG -n $PG -o none
echo "   Ready"

echo "══ 2. Keycloak"
# Deactivated revisions are EXCLUDED from `revision list` (verified: it returns 0 items),
# so the name must come from the app's latestRevisionName, which survives deactivation.
R=$(az containerapp show -n karaka-keycloak -g $RG --query properties.latestRevisionName -o tsv)
az containerapp revision activate -n karaka-keycloak -g $RG --revision "$R" -o none
printf "   waiting for the realm "
for _ in $(seq 1 90); do
  curl -fsS -m 8 -o /dev/null "$KC/realms/karaka" 2>/dev/null && { echo " up"; break; }
  printf "."; sleep 5
done

echo "══ 3. Service"
R=$(az containerapp show -n karaka -g $RG --query properties.latestRevisionName -o tsv)
az containerapp revision activate -n karaka -g $RG --revision "$R" -o none
printf "   waiting for health "
for _ in $(seq 1 90); do
  [[ "$(curl -sS -m 8 -o /dev/null -w '%{http_code}' "$APP/actuator/health" 2>/dev/null)" == 200 ]] \
    && { echo " up"; break; }
  printf "."; sleep 5
done
echo "══ Ready — $APP"
