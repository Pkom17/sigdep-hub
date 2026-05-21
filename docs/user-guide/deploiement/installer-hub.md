# Installer le hub SIGDEP

Ce guide s'adresse à l'équipe qui déploie le hub central (PNLS / SIGDEP).
À la fin, vous avez une stack `postgres + keycloak + ingestion-api +
console-api + nginx` qui répond sur une URL publique.

> **Pré-requis fonctionnels** : avoir validé la liste des régions /
> districts / sites à seeder dans `core.regions`, `core.districts`,
> `core.sites`. Les agents pourront s'y enregistrer ensuite.

## Pré-requis techniques

- **Serveur Linux** Ubuntu 22.04+ (ou équivalent) avec :
  - 4 vCPU minimum, 8 Go RAM.
  - 100 Go de stockage (croît avec les données ingérées).
  - Docker + docker compose v2.
- **Nom de domaine** (par exemple `sigdep.pnls.ci`) pointant sur l'IP
  publique du serveur.
- **Certificat TLS** (Let's Encrypt ou autorité officielle).

## Topologie

```
                  ┌─────────────┐
        :443 ─────│ nginx (TLS) │
                  └──────┬──────┘
                         │ (reverse-proxy en HTTP interne)
       ┌─────────────────┼──────────────────────┬───────────────┐
       ▼                 ▼                      ▼               ▼
 ┌──────────────┐  ┌────────────┐       ┌──────────────┐  ┌──────────┐
 │ console-web  │  │ console-api│       │ingestion-api │  │ Keycloak │
 │  (SPA nginx) │  │ /api/      │       │ /api/v1/sync │  │ /realms/ │
 └──────────────┘  └─────┬──────┘       └──────┬───────┘  └────┬─────┘
                         ▼                     ▼               ▼
                       Postgres            Postgres        Postgres
```

Tout est servi via le nginx front pour exposer un **seul** origin :
`https://sigdep.pnls.ci/`. Les agents et la console parlent à cet
origin ; nginx route en interne vers les 4 conteneurs (`console-web`
sert le SPA, `console-api` les écrans, `ingestion-api` les batches
des agents, `keycloak` l'authentification).

## Étape 1 — Cloner le repo

```bash
git clone git@github.com:ITECH-CI/sigdep-hub.git
cd sigdep-hub
```

## Étape 2 — Configurer les secrets

Créer `infra/.env` (non versionné) :

```ini
# Postgres
POSTGRES_DB=sigdep
POSTGRES_USER=sigdep
POSTGRES_PASSWORD=<mot_de_passe_fort>

# Keycloak
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<mot_de_passe_fort>
KC_DB_PASSWORD=<mot_de_passe_fort>
KC_HOSTNAME=https://sigdep.pnls.ci

# Console-api
KEYCLOAK_ADMIN_CLIENT_SECRET=<récupéré depuis Keycloak après import>
PUBLIC_ORIGIN=https://sigdep.pnls.ci

# Images publiées sur GHCR par le workflow release.yml.
# Owner = le compte GitHub qui pilote les releases ; remplacer
# pkom17 par itech-ci une fois la bascule officielle effectuée.
CONSOLE_API_IMAGE=ghcr.io/pkom17/sigdep-console-api:1.0.2
INGESTION_API_IMAGE=ghcr.io/pkom17/sigdep-ingestion-api:1.0.2
CONSOLE_WEB_IMAGE=ghcr.io/pkom17/sigdep-console-web:1.0.2
```

## Étape 3 — Démarrer la stack

```bash
cd infra
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

Au premier démarrage :

- **Postgres** se crée et Liquibase exécute les migrations (création
  du schéma `core` + `audit`, seeds des régions / districts / sites
  / identifier_types).
- **Keycloak** importe le realm `sigdep` depuis
  `infra/keycloak/realm-sigdep.json` (rôles, clients, thème SIGDEP).
- **ingestion-api** et **console-api** se connectent à Postgres.
- **nginx** termine la TLS et route les requêtes.

Vérifier :

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f
```

Tous les services doivent passer à `healthy` en 2-3 minutes.

## Étape 4 — Configurer le client Keycloak admin

L'`KEYCLOAK_ADMIN_CLIENT_SECRET` doit correspondre au client
`sigdep-console-admin` du realm. Après le premier démarrage :

1. Ouvrir `https://sigdep.pnls.ci/admin` (interface Keycloak admin).
2. Se connecter avec `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`.
3. Sélectionner le realm **sigdep**.
4. Ouvrir **Clients → sigdep-console-admin → Credentials**.
5. Régénérer le secret et le copier dans `.env`
   (`KEYCLOAK_ADMIN_CLIENT_SECRET`).
6. Redémarrer console-api :
   `docker compose restart console-api`.

## Étape 5 — Créer le premier SUPER_ADMIN

Via l'admin Keycloak (`/admin`) :

1. Realm `sigdep` → **Users** → **Add user**.
2. Username : `pkomena` (par exemple), email obligatoire.
3. **Credentials** → définir un mot de passe non temporaire.
4. **Role mapping** → assigner `SUPER_ADMIN`.

Vous pouvez maintenant ouvrir `https://sigdep.pnls.ci/` et vous
connecter avec ce compte. Les comptes suivants se créent via la
page **Utilisateurs** de la console — voir
[admin/gestion-utilisateurs.md](../admin/gestion-utilisateurs.md).

## Étape 6 — Préparer l'enregistrement des sites

Avant de déployer le premier agent, vérifier que tous les sites
ciblés sont présents dans `core.sites` :

```bash
docker exec sigdep-postgres psql -U sigdep -d sigdep \
  -c "SELECT code, name FROM core.sites ORDER BY code;"
```

Si un site manque, ajouter une ligne via une migration Liquibase
dédiée (ne pas écrire en direct dans la table). Voir le pattern dans
`ingestion-api/src/main/resources/db/changelog/seed/`.

## Étape 7 — Déployer le premier agent

Voir [installer-agent.md](installer-agent.md).

## Maintenance courante

### Sauvegarde Postgres

`docker exec sigdep-postgres pg_dump -U sigdep sigdep > backup-$(date +%F).sql`

À automatiser via cron + rotation. Conserver au moins 30 jours de
backups.

### Mise à jour de la stack

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

Liquibase appliquera automatiquement les nouvelles migrations.

### Reset partiel des données

Si besoin de purger les tables métier sans toucher au realm Keycloak
ni aux référentiels (typiquement après une phase de test) :

```bash
./infra/scripts/reset-hub.sh
```

Voir `infra/scripts/README.md` pour le détail.

### Reset complet

**Ne jamais faire `docker compose down -v`** sur un environnement
qui contient des données qu'on veut garder. Ça purge le volume
Postgres et donc aussi le realm Keycloak.

## En cas de problème

### Un service ne démarre pas

```bash
docker compose -f docker-compose.prod.yml logs <service>
```

### Keycloak n'accepte pas les redirects

Vérifier `KC_HOSTNAME` dans `.env` — il doit correspondre exactement
à l'URL publique. Toute modification nécessite un restart Keycloak.

### Postgres remplit le disque

Logique : SIGDEP-3 garde l'historique complet. Surveiller via
`docker exec sigdep-postgres df -h /var/lib/postgresql`.

Options : augmenter le disque, ou archiver et purger les anciennes
visites (à coordonner avec le PNLS).

## Voir aussi

- [installer-agent.md](installer-agent.md) — agent côté site.
- `infra/scripts/README.md` — scripts opérationnels.
- `sigdep-hub/docs/OPERATIONS.md` — runbook ops technique détaillé.
