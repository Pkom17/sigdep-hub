# sigdep-hub

Central server consolidating data from 550+ SIGDEP-3 sites. Maven multi-module.

## Modules

| Module          | Description                                                         |
|-----------------|---------------------------------------------------------------------|
| `core-domain`   | JPA entities, repositories, domain services. Library, no `main()`.  |
| `ingestion-api` | Spring Boot app on port `8090`. Receives sync batches from agents.  |
| `console-api`   | Spring Boot app on port `8041`. UI endpoints + serves React build.  |
| `console-web`   | React 18 + Vite frontend.                                           |

`infra/` holds Docker Compose for dev (Postgres + Keycloak) and the Keycloak
realm import directory.

Liquibase migrations are owned by `ingestion-api` (it is the writer; the console
runs with `spring.liquibase.enabled=false`).

## Build

```bash
# Install sigdep-contracts first (sibling project)
cd ../sigdep-contracts && mvn -DskipTests install

# Build the hub
cd ../sigdep-hub && mvn clean install
```

## Configuration

Each Spring Boot module (`ingestion-api`, `console-api`) keeps its own `.env`
at the module root. `spring-dotenv` loads it automatically at startup.

```bash
cp ingestion-api/.env.example ingestion-api/.env
cp console-api/.env.example   console-api/.env
# edit each file as needed
```

`.env` files are gitignored. `.env.example` files are the source of truth for
the variables each module recognises.

## Dev environment

The dev stack puts everything behind a single nginx origin
(`http://localhost:9000`) — exactly like the production topology, so OIDC
and CORS behave identically in both. Open `http://localhost:9000` in your
browser; the SPA, the API and Keycloak are all reachable from there.

```bash
cd infra && docker compose up -d            # postgres (5436) + keycloak (8180) + nginx (9000)

cd ../ingestion-api && ./run.sh --dev       # port 8090, runs Liquibase
cd ../console-api    && ./run.sh --dev      # port 8041
cd ../console-web    && npm install && npm run dev   # port 5173 (proxied through nginx)
```

| Layer                         | Port |
|-------------------------------|------|
| nginx (entry point)           | 9000 |
| Vite dev server (HMR)         | 5173 |
| console-api                   | 8041 |
| ingestion-api                 | 8090 |
| Keycloak (direct admin only)  | 8180 |
| Postgres                      | 5436 |

The dev Spring profile that bypassed auth has been removed; both APIs now
always validate JWTs against Keycloak. Make sure to be logged in before
calling any non-public endpoint.

## Production layout

`infra/docker-compose.prod.yml` reproduces the same single-origin topology
behind TLS. nginx terminates HTTPS and forwards `/realms/*` to Keycloak,
`/api/v1/sync/*` to the ingestion-api, the rest of `/api/*` to the
console-api, and serves the built SPA bundle. Keycloak runs in
`start --optimized` mode against its own Postgres database. The file is a
working skeleton: TLS certs, secrets and image tags have to be supplied
per environment before going live.

In all environments, `ingestion-api` and `console-api` deploy as **two
separate processes** (potentially scaled independently) sharing the same
PostgreSQL database.
