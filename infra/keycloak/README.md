# Keycloak realm import

Place the exported `realm-sigdep.json` file in this directory. It is mounted
read-only into the Keycloak container at `/opt/keycloak/data/import` (see
`docker-compose.yml`).

To bootstrap the realm on a fresh Keycloak instance, run:

```bash
docker compose exec keycloak \
  /opt/keycloak/bin/kc.sh import --dir /opt/keycloak/data/import
```

Realm name : `sigdep`
Clients :
- `sigdep-console`        — public, PKCE (frontend)
- `sigdep-console-api`    — bearer-only
- `sigdep-ingestion-api`  — bearer-only
- `sigdep-agent`          — confidential, client credentials

Roles : `SUPER_ADMIN`, `NATIONAL_VIEWER`, `REGIONAL_COORD`, `DISTRICT_COORD`,
`SITE_USER`, `ANALYST`, `IT_ADMIN`, `AUDITOR`.

## User profile (custom attributes)

Keycloak 25 rejects user attributes not declared in the realm's user-profile
config (unmanagedAttributePolicy defaults to DISABLED). We declare
`regionId`, `districtId` and `siteId` so the SIGDEP console can store the
geographic scope on user accounts.

The declaration lives in `userprofile-sigdep.json`. It is **not** auto-applied
by realm import — push it once after the realm exists:

```bash
docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user admin --password admin

docker cp infra/keycloak/userprofile-sigdep.json \
  sigdep-keycloak:/tmp/userprofile-sigdep.json

docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh update users/profile \
  -r sigdep -f /tmp/userprofile-sigdep.json
```
