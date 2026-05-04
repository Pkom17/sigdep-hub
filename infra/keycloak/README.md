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

Roles : `SUPER_ADMIN`, `NATIONAL_VIEWER`, `REGIONAL_COORD`, `SITE_USER`,
`ANALYST`, `IT_ADMIN`, `AUDITOR`.
