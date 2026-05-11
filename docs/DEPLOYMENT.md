# Deployment

`infra/docker-compose.prod.yml` is the production-shaped compose file.
It reproduces the dev topology (single nginx origin, Keycloak fronted by
nginx, separate console-api / ingestion-api processes, shared
PostgreSQL) but with TLS, secrets and image tags pulled from the
environment.

The file is a **working skeleton**, not a turnkey deployment. Every
placeholder labelled `change-me` must be replaced before a production
install. This document is the checklist.

## Prerequisites

- A host with Docker 24+ and Compose v2.
- A DNS name pointing at the host (e.g. `sigdep.example.org`).
- TLS certificates for that name. Let's Encrypt via certbot is fine; for
  staging you can use mkcert.
- A separately backed-up Postgres instance, or trust the local volume
  with backups (see below).
- Network rules: only port 443 needs to be reachable from the outside;
  agents POST to the same hostname over HTTPS.

## One-time setup

### 1. Provide TLS certs

Drop your full chain and private key into `infra/nginx/certs/` next to
`docker-compose.prod.yml`:

```
infra/nginx/certs/
├── fullchain.pem
└── privkey.pem
```

`nginx.prod.conf` mounts this directory read-only into the container.

### 2. Build the SPA bundle

```bash
cd console-web && npm install && npm run build
mkdir -p ../infra/web
cp -R dist/* ../infra/web/
```

`infra/web/` is mounted into the nginx container at
`/usr/share/nginx/html`. Repeat after every SPA change.

### 3. Tag and publish the API images

The compose file references `CONSOLE_API_IMAGE` and
`INGESTION_API_IMAGE`. Build them with the Spring Boot Maven plugin or
your CI of choice and push to a registry the host can pull from
(GitHub Container Registry, Docker Hub, internal Harbor, …).

```bash
mvn -pl console-api,ingestion-api -am -DskipTests package
# Then build container images on top of the resulting fat JARs. A
# minimal Dockerfile is one COPY + ENTRYPOINT java -jar. CI templates
# for this are on the roadmap.
```

### 4. Set secrets

`docker-compose.prod.yml` reads sensitive values from environment
variables. Put them in `/etc/sigdep/sigdep-hub.env` (mode 0600) or use
your secret manager. At minimum:

```bash
POSTGRES_PASSWORD=...
KEYCLOAK_ADMIN_PASSWORD=...
KC_DB_PASSWORD=...
KEYCLOAK_ADMIN_CLIENT_SECRET=...
KC_HOSTNAME=https://sigdep.example.org
PUBLIC_ORIGIN=https://sigdep.example.org
CONSOLE_API_IMAGE=ghcr.io/itech-ci/sigdep-console-api:0.1.0
INGESTION_API_IMAGE=ghcr.io/itech-ci/sigdep-ingestion-api:0.1.0
```

Then run compose with the file:

```bash
docker compose --env-file /etc/sigdep/sigdep-hub.env \
  -f docker-compose.prod.yml up -d
```

### 5. Bootstrap the Keycloak realm

The first `--import-realm` start loads `realm-sigdep.json`. Then apply
the user-profile (see `infra/keycloak/README.md`). Finally, **change
every default password** in the realm before the first user logs in:

- The Keycloak master admin (`KEYCLOAK_ADMIN_PASSWORD`).
- The default users (`pkomena`, `national-viewer`, `site-user`) shipped
  in `realm-sigdep.json` — these are dev-only, delete or reset them.
- The `sigdep-console-admin` and `sigdep-agent` client secrets.

### 6. Wire the agents

Each `sigdep-sync` site agent needs:

- `SIGDEP_HUB_URL=https://sigdep.example.org`
- `SIGDEP_KEYCLOAK_URL=https://sigdep.example.org`
- `SIGDEP_AGENT_CLIENT_SECRET=<from the realm>`
- `SIGDEP_SITE_CODE=<the local site code>`

See `sigdep-sync/README.md` for the install procedure (systemd unit on
Linux sites, WinSW / NSSM wrapper on Windows sites).

## Operational concerns

### Backups

```bash
# Daily dump of the consolidated database
docker exec sigdep-postgres pg_dump -U sigdep sigdep \
  | gzip > /var/backups/sigdep-$(date +%F).sql.gz

# Keep at least 30 days. Encrypt offsite if patient data is on it.
```

The Keycloak database (separate, `KC_DB_NAME=keycloak`) also needs
backups — user accounts, realms, federation config live there.

### Upgrades

1. Build the new images, tag them.
2. Update `CONSOLE_API_IMAGE` / `INGESTION_API_IMAGE` in the env file.
3. `docker compose pull && docker compose up -d`.
4. `ingestion-api` will run any new Liquibase migrations on startup.

Liquibase migrations should be **forward-compatible**: an older
ingestion-api should keep working against a database that has had a new
schema migration applied. In practice, this means: only add columns
(don't drop), never rename, allow null first then backfill.

### Monitoring

- Both APIs expose `/actuator/health`, `/actuator/info`,
  `/actuator/prometheus`. Nginx routes `/actuator/*` to console-api;
  ingestion-api's actuator stays on its internal port — scrape it from
  the Prometheus container directly.
- Keycloak's management interface is on its own port (`8080` inside the
  container) — don't expose it to the public network.

### TLS rotation

Replace the certs in `infra/nginx/certs/` and run
`docker exec sigdep-nginx nginx -s reload`. No downtime.

### Disaster recovery

Cold-restoring from a daily dump:

```bash
docker compose -f docker-compose.prod.yml down
# Restore the Postgres volume from backup, OR:
gunzip -c /var/backups/sigdep-YYYY-MM-DD.sql.gz | \
  docker exec -i sigdep-postgres psql -U sigdep -d sigdep
docker compose -f docker-compose.prod.yml up -d
```

Agents will retry pending batches from their local SQLite buffer, so up
to a few hours of in-flight syncs are recovered automatically.
