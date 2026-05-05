# sigdep-hub

Central server consolidating data from 550+ SIGDEP-3 sites. Maven multi-module.

## Modules

| Module          | Description                                                         |
|-----------------|---------------------------------------------------------------------|
| `core-domain`   | JPA entities, repositories, domain services. Library, no `main()`.  |
| `ingestion-api` | Spring Boot app on port `8090`. Receives sync batches from agents.  |
| `console-api`   | Spring Boot app on port `8081`. UI endpoints + serves React build.  |
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

```bash
cd infra && docker compose up -d            # postgres (5436) + keycloak (8180)

cd ../ingestion-api && ./run.sh --dev       # port 8090, runs Liquibase
cd ../console-api    && ./run.sh --dev      # port 8081

cd ../console-web && npm install && npm run dev   # port 5173, proxies /api → 8081
```

To enable the dev profile on the hub APIs (auth disabled on `/api/v1/sync/**`),
set `SPRING_PROFILES_ACTIVE=dev` in the module's `.env`.

## Production layout

`ingestion-api` and `console-api` are deployed as **two separate processes**
(potentially scaled independently) sharing the same PostgreSQL database. The
React `console-web` build is copied into `console-api/src/main/resources/static/`
before packaging, so the console serves the SPA and the API from a single
process.
