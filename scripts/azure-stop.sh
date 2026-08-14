#!/usr/bin/env bash
# Stops everything billable that can be stopped, without deleting anything.
#
# Deactivating revisions is used rather than scaling to zero: the apps already
# scale to zero on idle, so only deactivation actually prevents cold starts.
# Postgres compute stops billing; its storage does not. Azure force-starts a
# stopped flexible server after 7 days, so this is not indefinite.
set -euo pipefail
RG=karaka
for A in karaka karaka-keycloak; do
  for R in $(az containerapp revision list -n $A -g $RG --query "[?properties.active].name" -o tsv); do
    az containerapp revision deactivate -n $A -g $RG --revision "$R" -o none
    echo "   deactivated $R"
  done
done
az postgres flexible-server stop -g $RG -n karaka-pg-578 -o none
echo "   Postgres stopped (Azure force-starts it after 7 days)"
