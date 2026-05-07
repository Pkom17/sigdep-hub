#!/usr/bin/env bash
# Import the sigdep realm into the running Keycloak container.
#
# Usage:
#   ./infra/scripts/import_realm.sh             # idempotent: skips if realm exists
#   ./infra/scripts/import_realm.sh --override  # delete the realm first, then import
#
# Requires: docker compose stack up (sigdep-keycloak container running) and
# realm-sigdep.json present under infra/keycloak/.

set -euo pipefail

CONTAINER="${CONTAINER:-sigdep-keycloak}"
REALM_FILE="/opt/keycloak/data/import/realm-sigdep.json"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
OVERRIDE=false

if [[ "${1:-}" == "--override" ]]; then
  OVERRIDE=true
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "✗ Keycloak container '${CONTAINER}' is not running."
  echo "  Start it with: docker compose -f infra/docker-compose.yml up -d keycloak"
  exit 1
fi

echo "→ Logging into Keycloak admin (kcadm.sh) ..."
docker exec "${CONTAINER}" /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user "${ADMIN_USER}" \
  --password "${ADMIN_PASS}" >/dev/null

REALM_EXISTS=$(docker exec "${CONTAINER}" /opt/keycloak/bin/kcadm.sh get realms/sigdep 2>/dev/null || true)

if [[ -n "${REALM_EXISTS}" ]]; then
  if [[ "${OVERRIDE}" == "true" ]]; then
    echo "→ Realm 'sigdep' exists — deleting (--override)."
    docker exec "${CONTAINER}" /opt/keycloak/bin/kcadm.sh delete realms/sigdep
  else
    echo "✓ Realm 'sigdep' already exists — nothing to do (use --override to recreate)."
    exit 0
  fi
fi

echo "→ Importing realm from ${REALM_FILE} ..."
docker exec "${CONTAINER}" /opt/keycloak/bin/kcadm.sh create realms -f "${REALM_FILE}"

echo "✓ Realm 'sigdep' imported."
echo "  - Console:      http://localhost:8180/realms/sigdep/account"
echo "  - Admin URL:    http://localhost:8180/admin/master/console/#/sigdep"
echo "  - Test users:   pkomena / national-viewer / site-user (password: sigdep)"
