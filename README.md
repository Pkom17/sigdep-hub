# sigdep-hub

Central server consolidating HIV care data from 550+ SIGDEP-3 sites
(programme national de lutte contre le sida, Côte d’Ivoire). It receives
sync batches from edge agents running at each site, stores them in a single
PostgreSQL database, computes PEPFAR / national indicators, and serves a
React console for national, regional, district and site users.

![SIGDEP-3 dashboard — authenticated view](docs/screenshots/overview.png)

## Place in the SIGDEP-3 platform

This repo is one of three projects that make up SIGDEP-3:

| Project                                                            | Role                                                                   |
| ------------------------------------------------------------------ | ---------------------------------------------------------------------- |
| [`sigdep-contracts`](https://github.com/ITECH-CI/sigdep-contracts) | Shared DTOs and API contracts (Maven library)                          |
| [`sigdep-sync`](https://github.com/ITECH-CI/sigdep-sync)           | Edge agent deployed on each site — reads local OpenMRS, pushes batches |
| **`sigdep-hub`** (this repo)                                       | Central server — receives batches, indicators, console                 |

The agent on each site reads its local OpenMRS MySQL database, transforms
the records into the canonical DTOs defined in `sigdep-contracts`, and
POSTs them to `sigdep-hub`’s `ingestion-api`. The `console-api` then
serves indicators and listings to authenticated users via the React SPA.

```
   ┌──────────────────┐                  ┌────────────── sigdep-hub ───────────────┐
   │  site OpenMRS    │                  │                                          │
   │  (MySQL, read)   │                  │   ingestion-api ──┐                      │
   └────────┬─────────┘                  │                   ▼                      │
            │   sigdep-sync agent        │            PostgreSQL                    │
            │   (Java, SQLite buffer)    │                   ▲                      │
            └──── HTTPS batches ─────────┼───►  console-api ─┘──► React console     │
                                         └──────────────────────────────────────────┘
```

## Modules

| Module          | Description                                                        |
| --------------- | ------------------------------------------------------------------ |
| `core-domain`   | JPA entities, repositories, domain services. Library, no `main()`. |
| `ingestion-api` | Spring Boot app on port `8090`. Receives sync batches from agents. |
| `console-api`   | Spring Boot app on port `8041`. Console endpoints + serves SPA.    |
| `console-web`   | React 18 + Vite + Tailwind frontend.                               |
| `infra/`        | docker-compose for dev and prod, nginx configs, Keycloak realm.    |

Liquibase migrations are owned by `ingestion-api` (the only writer); the
console runs with `spring.liquibase.enabled=false`.

## Quickstart (dev)

Prerequisites: **JDK 17+**, **Maven 3.9+**, **Node 20+**, **Docker** with
Compose v2. The whole stack runs locally on a laptop.

```bash
# 1. Build the contracts artefact (sibling repo) into your local ~/.m2
git clone https://github.com/ITECH-CI/sigdep-contracts
cd sigdep-contracts && mvn -DskipTests install && cd ..

# 2. Clone and build the hub
git clone https://github.com/ITECH-CI/sigdep-hub
cd sigdep-hub
mvn -DskipTests install

# 3. Start the infra (Postgres + Keycloak + nginx)
cd infra && docker compose up -d && cd ..

# 4. Apply the Keycloak userprofile (one-time, after the realm is imported)
docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user admin --password admin
docker cp infra/keycloak/extras/userprofile-sigdep.json \
  sigdep-keycloak:/tmp/userprofile-sigdep.json
docker exec sigdep-keycloak /opt/keycloak/bin/kcadm.sh update users/profile \
  -r sigdep -f /tmp/userprofile-sigdep.json

# 5. Bring up the three processes (three terminals)
cp ingestion-api/.env.example ingestion-api/.env
cp console-api/.env.example   console-api/.env

(cd ingestion-api && ./run.sh --dev)             # port 8090, runs Liquibase
(cd console-api    && ./run.sh --dev)            # port 8041
(cd console-web    && npm install && npm run dev) # port 5173 (proxied)
```

Open **http://localhost:9000** in your browser. Default users:

| Username          | Password | Roles                                        |
| ----------------- | -------- | -------------------------------------------- |
| `pkomena`         | `sigdep` | `SUPER_ADMIN`, `IT_ADMIN`, `NATIONAL_VIEWER` |
| `national-viewer` | `sigdep` | `NATIONAL_VIEWER`                            |
| `site-user`       | `sigdep` | `SITE_USER` (needs a `siteId` attribute)     |

| Layer                        | Port | Notes                                          |
| ---------------------------- | ---- | ---------------------------------------------- |
| nginx (entry point)          | 9000 | Single origin for the whole stack              |
| Vite dev server (HMR)        | 5173 | Reached via the nginx proxy                    |
| console-api                  | 8041 | Spring Boot                                    |
| ingestion-api                | 8090 | Spring Boot, owns the Liquibase changelog      |
| Keycloak (direct admin only) | 8180 | Day-to-day traffic goes through nginx on :9000 |
| Postgres                     | 5436 | Database `sigdep`, user `sigdep`/`sigdep`      |

## Documentation

### Pour les déployeurs et les utilisateurs (français)

Le guide utilisateur, organisé par rôle, vit dans
[`docs/user-guide/`](docs/user-guide/README.md). Entrées les plus
utiles pour un pilote :

- [Installer le hub](docs/user-guide/deploiement/installer-hub.md) —
  procédure pas-à-pas pour la stack centrale.
- [Installer un agent](docs/user-guide/deploiement/installer-agent.md) —
  3 modes (systemd, Docker, Windows WinSW).
- [Checklist de déploiement pilote](docs/user-guide/deploiement/pilote-checklist.md) —
  tracker à cocher pour passer en production sur N sites.

### Pour les développeurs (anglais)

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — module topology, auth model
  (JWT + AuthScope), the geo-scoping rules, how indicators are computed.
- [docs/OPERATIONS.md](docs/OPERATIONS.md) — day-to-day commands (kcadm,
  Liquibase, run.sh), troubleshooting recipes for the issues we hit during
  build-up (CORS, user attributes, profile dev, …).
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — historical install notes
  based on `infra/docker-compose.prod.yml`. For a real install today,
  follow the French [Installer le hub](docs/user-guide/deploiement/installer-hub.md)
  instead — it reflects the GHCR-based release flow.
- [CONTRIBUTING.md](CONTRIBUTING.md) — git workflow, commit conventions,
  code style.
- [infra/keycloak/README.md](infra/keycloak/README.md) — realm import,
  user-profile attributes, kcadm.sh snippets.

## Container images

Each `v*.*.*` tag triggers a release workflow that publishes three
images to GHCR:

```
ghcr.io/<owner>/sigdep-ingestion-api:<version>
ghcr.io/<owner>/sigdep-console-api:<version>
ghcr.io/<owner>/sigdep-console-web:<version>
```

`<owner>` defaults to the GitHub user/org running the release; set the
repo variable `IMAGE_REGISTRY` to override (for example
`ghcr.io/itech-ci`). These tags are what the production
docker-compose pulls — see
[Installer le hub](docs/user-guide/deploiement/installer-hub.md).

## License

To be decided in a plenary session with the HMIS TWG; no license file is
shipped yet. In the meantime, treat the contents as "all rights reserved
by I-TECH Côte d'Ivoire and the PNLS programme".
