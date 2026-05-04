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

## Dev environment

```bash
cd infra && docker compose up -d            # postgres (5436) + keycloak (8180)

cd ../ingestion-api && mvn spring-boot:run  # port 8090, runs Liquibase
cd ../console-api    && mvn spring-boot:run # port 8081

cd ../console-web && npm install && npm run dev   # port 5173, proxies /api → 8081
```

## Production layout

`ingestion-api` and `console-api` are deployed as **two separate processes**
(potentially scaled independently) sharing the same PostgreSQL database. The
React `console-web` build is copied into `console-api/src/main/resources/static/`
before packaging, so the console serves the SPA and the API from a single
process.
