# Operations

Day-to-day recipes for working on or with `sigdep-hub`. The
[ARCHITECTURE.md](ARCHITECTURE.md) doc explains the why; this one focuses
on commands you actually type.

## Starting and stopping the stack

```bash
# Start: postgres + keycloak + nginx all in one go
cd infra && docker compose up -d

# Stop everything (state preserved)
cd infra && docker compose down

# Nuke state — DB + Keycloak realm are reset on next `up`
cd infra && docker compose down -v
```

Once infra is up, the three Spring Boot / Vite processes run on the host
(not in containers, to keep iteration fast):

```bash
(cd ingestion-api && ./run.sh --dev)              # 8090, runs Liquibase
(cd console-api    && ./run.sh --dev)             # 8041
(cd console-web    && npm run dev)                # 5173 (via nginx :9000)
```

`run.sh --dev` uses Maven `spring-boot:run` (picks up local code changes
and reinstalls `core-domain` first). `run.sh` without `--dev` runs the
packaged JAR — closer to prod.

## Keycloak — kcadm.sh recipes

The realm import in `docker-compose.yml` only bootstraps `realm-sigdep.json`.
A few things are applied **once** by hand after the realm exists:

### Apply the user-profile (custom attributes regionId/districtId/siteId)

Keycloak 25 silently drops user attributes that aren't declared in the
user-profile. This is a known footgun we hit during build-up.

```bash
docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user admin --password admin

docker cp infra/keycloak/extras/userprofile-sigdep.json \
  sigdep-keycloak:/tmp/userprofile-sigdep.json

docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh update users/profile \
  -r sigdep -f /tmp/userprofile-sigdep.json
```

### Inspect a user’s attributes

`--fields attributes` truncates them; use the user’s full URL to see
them:

```bash
SITE_USER_ID=$(docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh get users \
  -r sigdep -q username=site-user --fields id --format csv --noquotes | tail -1)

docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh get "users/$SITE_USER_ID" \
  -r sigdep | python3 -m json.tool
```

### Grant a user a geographic scope (one-off)

Easier from the console (`/app/users`), but if you need to script it:

```bash
docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh update "users/$SITE_USER_ID" \
  -r sigdep \
  -s 'attributes.regionId=["32"]' \
  -s 'attributes.districtId=["110"]' \
  -s 'attributes.siteId=["7098"]'
```

### Reset the admin session

`kcadm.sh` tokens expire after a few minutes. If you see
`Session has expired`, just `config credentials` again with the same
flags as above.

## Liquibase / database

Liquibase is part of `ingestion-api`. It runs on every startup.

```bash
# Inspect the changelog history
docker exec sigdep-postgres psql -U sigdep -d sigdep \
  -c "SELECT id, author, dateexecuted FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 10;"

# Connect to the DB
docker exec -it sigdep-postgres psql -U sigdep -d sigdep
```

To add a migration: drop a new XML file in
`ingestion-api/src/main/resources/db/changelog/v1.0/`, register it in
`db.changelog-master.xml`, and restart `ingestion-api`. **Never edit a
migration that has already been applied** — write a new one.

## Sync audit (`audit.sync_batch`)

Every call to `/api/v1/sync/*` writes a row. The Synchronisation page in
the console reads it; for quick CLI diagnostics:

```sql
-- Recent batches
SELECT id, site_code, entity_type, received_count, accepted, rejected,
       status, duration_ms, finished_at
FROM audit.sync_batch
ORDER BY finished_at DESC
LIMIT 20;

-- Sites that haven't synced in 7 days
SELECT s.code, s.name, s.last_sync_at
FROM core.sites s
WHERE s.last_sync_at IS NULL OR s.last_sync_at < NOW() - INTERVAL '7 days'
ORDER BY s.last_sync_at NULLS FIRST;
```

## Troubleshooting

Issues we hit while building, with the diagnosis baked in.

### `bad SQL grammar` / `operator does not exist: bigint = character varying`

A query uses both `?`-bound geo args and other `?`-bound params, and the
ordering in the SQL string drifted from the ordering in the args array.
The fix is usually to **inline the constants** (concept UUIDs, etc.) and
keep only the dynamic args as `?`. See the fix in `ClinicService` from
mid-May 2026 for the canonical pattern.

### `403 insufficient_scope` on a listing endpoint

The user has no role in the endpoint's `@PreAuthorize` whitelist. Check
the controller — `SITE_USER` and `DISTRICT_COORD` must be listed alongside
the national roles for the geographic scoping to work. If they aren't,
the request is rejected *before* `AuthScope` even runs.

### Site-scoped user still sees everything

In order:

1. Did you remove `SPRING_PROFILES_ACTIVE=dev` from `console-api/.env`?
   The dev profile bypasses auth and `AuthScope` never gets a JWT.
2. Does the user have the right attributes in Keycloak? Read them with
   the full-URL trick above (`--fields attributes` lies).
3. Has the user logged out and back in since the attributes changed?
   The JWT is only refreshed at login.
4. Are the protocol-mappers still on the `sigdep-console` client? They
   project the user attributes into the access-token claims. Inspect
   with `kcadm.sh get clients/<id>/protocol-mappers/models -r sigdep`.

### CORS errors on Firefox

If you're hitting `/api/*` and Firefox refuses with "CORS désactivé", you
are probably on a port that isn't behind the nginx proxy. Always use
`http://localhost:9000` in dev — that's the whole point of the nginx
single-origin setup. Direct hits to `:5173`, `:8041` or `:8180` work in
Chrome but not in Firefox with strict tracking protection.

### Keycloak loops between login screens

Usually means the JWT issuer in the token doesn't match
`KEYCLOAK_ISSUER_URI` configured in console-api / ingestion-api. After
changing `KC_HOSTNAME`, restart both APIs so they re-read their config.

### `Liquibase: ChangeSet ... has already been ran with checksum`

You modified an applied migration. **Don't** edit it back — write a new
migration that produces the desired state. If you really need to fix
the original (only in dev, never in prod), update the checksum in
`databasechangelog` or `docker compose down -v` and let it re-run from
zero.

### Vite dev server returns 502 from nginx

nginx can't reach Vite. Two common causes:

- Vite is bound to 127.0.0.1 only. Make sure `vite.config.ts` has
  `server.host: true`.
- The Vite process is down — check the `npm run dev` terminal.

If you get `Blocked request. This host ("vite") is not allowed.`, the
upstream name leaked into the Host header. Solutions: keep `vite` in
`allowedHosts`, or make sure every `proxy_set_header Host $host;` is
restated inside each nginx `location` block (nginx resets inheritance
when you add any `proxy_set_header` in a block).

## Cleaning a stale dev environment

If things get weird (db schema mismatch, ghost user attributes, mystery
401s):

```bash
cd infra
docker compose down -v
docker compose up -d
# Then re-apply the userprofile and the kcadm tweaks (see above)
```

Database + Keycloak state are reset; the realm gets re-imported from
`realm-sigdep.json` on the first start.
