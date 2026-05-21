# Installer un agent de synchronisation

Ce guide s'adresse aux **techniciens** qui installent l'agent
`sigdep-sync` sur le poste d'un site. À la fin, l'agent extrait les
données de l'OpenMRS local et les envoie au hub central toutes les
15 minutes.

## Pré-requis

Sur le poste cible (site) :

- **Système** : Linux (Ubuntu 22.04+ ou équivalent) ou Windows
  (via `winsw` pour la déclaration de service — voir
  `sigdep-sync/README.md`).
- **Java 17** (`apt install openjdk-17-jre-headless`).
- **MySQL** local (= la base OpenMRS du site). L'agent y lit en
  lecture seule.
- **Accès réseau** vers le hub central (HTTPS, port 443).
- **Accès réseau** vers le Keycloak central (port 443).

Côté hub (à demander à l'équipe SIGDEP centrale) :

- **Code du site** (`SIGDEP_SITE_CODE`) — identifiant unique du site
  dans `core.sites` (ex. `CHU_TREICHVILLE`).
- **Client secret Keycloak** (`SIGDEP_KEYCLOAK_CLIENT_SECRET`) pour
  l'authentification machine-to-machine.

## Étape 1 — Créer l'utilisateur système

```bash
sudo useradd -r -s /bin/false sigdep-agent
sudo mkdir -p /var/lib/sigdep-agent /etc/sigdep-sync /opt/sigdep-sync
sudo chown -R sigdep-agent:sigdep-agent /var/lib/sigdep-agent
```

## Étape 2 — Déployer le binaire

```bash
sudo cp sigdep-sync-<version>.jar /opt/sigdep-sync/sigdep-sync.jar
sudo chown sigdep-agent:sigdep-agent /opt/sigdep-sync/sigdep-sync.jar
sudo chmod 750 /opt/sigdep-sync/sigdep-sync.jar
```

> Le binaire est produit par `mvn -pl sigdep-sync package` ou fourni
> par l'équipe SIGDEP centrale.

## Étape 3 — Configurer les variables d'environnement

Créer `/etc/sigdep-sync/sigdep-sync.env` :

```ini
# Identification du site
SIGDEP_SITE_CODE=CHU_TREICHVILLE

# Hub central
SIGDEP_CENTRAL_API_URL=https://sigdep.pnls.ci

# OpenMRS local (lecture seule)
SIGDEP_LOCAL_DB_URL=jdbc:mysql://localhost:3306/openmrs?readOnly=true&useSSL=false
SIGDEP_LOCAL_DB_USER=sigdep_reader
SIGDEP_LOCAL_DB_PASSWORD=...

# Buffer local (laisser le défaut sauf besoin spécifique)
SIGDEP_BUFFER_PATH=/var/lib/sigdep-agent/buffer.sqlite

# Keycloak central
SIGDEP_KEYCLOAK_URL=https://sigdep.pnls.ci
SIGDEP_KEYCLOAK_CLIENT_ID=sigdep-agent
SIGDEP_KEYCLOAK_CLIENT_SECRET=...

# Paramètres avancés (laisser le défaut)
SIGDEP_BATCH_SIZE=500
SIGDEP_SYNC_INTERVAL_MINUTES=15
SIGDEP_MAX_REJECT_ATTEMPTS=10
```

Protéger le fichier :

```bash
sudo chown root:sigdep-agent /etc/sigdep-sync/sigdep-sync.env
sudo chmod 640 /etc/sigdep-sync/sigdep-sync.env
```

## Étape 4 — Créer l'utilisateur MySQL en lecture seule

Sur l'OpenMRS local :

```sql
CREATE USER 'sigdep_reader'@'localhost' IDENTIFIED BY '<mot_de_passe>';
GRANT SELECT ON openmrs.* TO 'sigdep_reader'@'localhost';
FLUSH PRIVILEGES;
```

L'agent n'écrit **jamais** dans OpenMRS, uniquement SELECT.

## Étape 5 — Installer le service systemd

```bash
sudo cp sigdep-sync/packaging/systemd/sigdep-sync.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now sigdep-sync
```

Vérifier le démarrage :

```bash
sudo systemctl status sigdep-sync
sudo journalctl -u sigdep-sync -f
```

Vous devez voir, dans les premières secondes :

```
INFO  Sync cycle started. N extractor(s) registered.
INFO  Enqueued X records (...)
INFO  Flushed Y batch(es) for ENTITY ...
```

## Étape 6 — Valider côté hub

Demander à un administrateur du hub de vérifier que :

1. Le site apparaît dans **/app/sites** avec un statut « En ligne ».
2. La page **/app/sync** liste des batches récents pour ce site.
3. L'onglet **Rejets** ne montre pas une explosion de rejets.

Au premier cycle, l'agent peut envoyer plusieurs milliers
d'enregistrements (toute l'historique). C'est normal — laissez-le
travailler quelques heures, puis revérifiez.

## Mise à jour

Pour déployer une nouvelle version du JAR :

```bash
sudo systemctl stop sigdep-sync
sudo cp sigdep-sync-<nouvelle_version>.jar /opt/sigdep-sync/sigdep-sync.jar
sudo systemctl start sigdep-sync
sudo journalctl -u sigdep-sync -f   # vérifier le démarrage
```

L'outbox SQLite et la watermark sont **préservées** : l'agent reprend
exactement où il en était.

## En cas de problème

### L'agent ne démarre pas

```bash
sudo journalctl -u sigdep-sync --no-pager | tail -50
```

Causes fréquentes :

- **Java pas installé** : `which java` ; sinon `apt install openjdk-17-jre-headless`.
- **Fichier env non lisible** par l'utilisateur `sigdep-agent` : vérifier les
  permissions.
- **Buffer non accessible** : vérifier `/var/lib/sigdep-agent` et le `chown`.

### L'agent démarre mais n'envoie rien

- **Vérifier la connectivité** : `curl -fsSL <SIGDEP_CENTRAL_API_URL>/actuator/health`.
- **Vérifier Keycloak** : `curl -fsSL <SIGDEP_KEYCLOAK_URL>/realms/sigdep`.
- **Vérifier MySQL local** : `mysql -u sigdep_reader -p openmrs -e 'SELECT 1'`.

### Beaucoup de rejets

Voir [admin/investiguer-rejets.md](../admin/investiguer-rejets.md).

### Reset complet de l'agent

Si l'outbox est dans un état corrompu, lancer
`sigdep-sync/scripts/reset-agent.sh` (voir
`sigdep-sync/scripts/README.md`).

## Voir aussi

- [installer-hub.md](installer-hub.md) — déploiement côté central.
- `sigdep-sync/README.md` — documentation technique de l'agent.
- `sigdep-sync/packaging/systemd/sigdep-sync.service` — unit systemd
  de référence.
