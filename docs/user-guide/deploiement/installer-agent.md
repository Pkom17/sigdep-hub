# Installer un agent de synchronisation

Ce guide s'adresse aux **techniciens** qui installent l'agent
`sigdep-sync` sur le poste d'un site. À la fin, l'agent extrait les
données de l'OpenMRS local et les envoie au hub central toutes les
15 minutes.

Deux modes d'installation sont supportés ; choisissez selon les
contraintes du site :

- **Mode A — systemd** (recommandé en production stable). L'agent
  tourne comme un service Linux natif. Nécessite Java 17 sur le poste.
- **Mode B — Docker** (recommandé en intégration ou si le site a déjà
  une stack Docker). Image pré-construite, aucun Java à installer
  sur l'hôte.

Les deux modes partagent les pré-requis ci-dessous et la validation
côté hub à la fin.

## Pré-requis communs

Sur le poste cible (site) :

- **Système** : Linux (Ubuntu 22.04+ ou équivalent).
- **MySQL** local (= la base OpenMRS du site). L'agent y lit en
  lecture seule.
- **Accès réseau sortant** vers le hub central (HTTPS, port 443).
- **Accès réseau sortant** vers le Keycloak central (port 443).

Côté hub (à demander à l'équipe SIGDEP centrale) :

- **Code du site** (`SIGDEP_SITE_CODE`) — identifiant unique du site
  dans `core.sites` (ex. `CHU_TREICHVILLE`).
- **Client secret Keycloak** (`SIGDEP_KEYCLOAK_CLIENT_SECRET`) pour
  l'authentification machine-to-machine.

## Préparer l'utilisateur MySQL en lecture seule

Cette étape est commune aux deux modes — l'agent ne doit jamais
écrire dans OpenMRS.

Sur l'OpenMRS local :

```sql
CREATE USER 'sigdep_reader'@'%' IDENTIFIED BY '<mot_de_passe>';
GRANT SELECT ON openmrs.* TO 'sigdep_reader'@'%';
FLUSH PRIVILEGES;
```

> Le `'%'` autorise les connexions depuis n'importe quelle IP. Si
> votre MySQL est dédié au site et n'expose pas son port en dehors,
> c'est sans risque. Sinon, restreignez à `'localhost'` (mode A) ou
> à l'IP du container Docker (mode B).

---

# Mode A — systemd

## A.1 — Installer Java 17

```bash
sudo apt install -y openjdk-17-jre-headless
```

## A.2 — Créer l'utilisateur système

```bash
sudo useradd -r -s /bin/false sigdep-agent
sudo mkdir -p /var/lib/sigdep-agent /etc/sigdep-sync /opt/sigdep-sync
sudo chown -R sigdep-agent:sigdep-agent /var/lib/sigdep-agent
```

## A.3 — Déployer le binaire

```bash
sudo cp sigdep-sync-<version>.jar /opt/sigdep-sync/sigdep-sync.jar
sudo chown sigdep-agent:sigdep-agent /opt/sigdep-sync/sigdep-sync.jar
sudo chmod 750 /opt/sigdep-sync/sigdep-sync.jar
```

> Le binaire est produit par `mvn -pl sigdep-sync package` ou fourni
> par l'équipe SIGDEP centrale.

## A.4 — Configurer les variables d'environnement

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

## A.5 — Installer le service systemd

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

---

# Mode B — Docker

L'image officielle est publiée sur GitHub Container Registry :
`ghcr.io/<owner>/sigdep-sync:<tag>` (par exemple
`ghcr.io/pkom17/sigdep-sync:1.0.1` pour la version de test).

## B.1 — Installer Docker

```bash
sudo apt install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER
```

Déconnectez-vous puis reconnectez-vous pour que l'appartenance au
groupe `docker` prenne effet.

## B.2 — Préparer le dossier d'installation

```bash
mkdir -p ~/sigdep-sync
cd ~/sigdep-sync
```

Téléchargez les deux fichiers de référence :

```bash
curl -O https://raw.githubusercontent.com/<owner>/sigdep-sync/master/deploy/docker-compose.site.yml
curl -O https://raw.githubusercontent.com/<owner>/sigdep-sync/master/deploy/.env.example
mv .env.example .env
```

> En production, copiez ces fichiers depuis votre propre dépôt
> ou depuis la livraison fournie par l'équipe SIGDEP.

## B.3 — Choisir le scénario réseau

`docker-compose.site.yml` contient **trois scénarios commentés** dans
le bloc `services.sigdep-sync` ; décommentez **un seul** :

- **Scénario 1** — OpenMRS tourne sur la machine hôte (MySQL écoute
  sur `localhost:3306` du serveur). Décommentez le bloc
  `extra_hosts: ["host.docker.internal:host-gateway"]`. Dans `.env`,
  réglez `SIGDEP_LOCAL_DB_URL=jdbc:mysql://host.docker.internal:3306/openmrs?...`.

- **Scénario 2** — OpenMRS est lui-même dans un container Docker sur
  la même machine. Décommentez le bloc `networks: [openmrs_default]`
  en remplaçant `openmrs_default` par le nom réel (visible via
  `docker network ls`). Dans `.env`,
  `SIGDEP_LOCAL_DB_URL=jdbc:mysql://<nom_du_container_mysql>:3306/openmrs?...`.

- **Scénario 3** — OpenMRS tourne sur une machine distante du LAN.
  Aucune configuration réseau spéciale, l'agent sort en bridge par
  défaut. Vérifiez : firewall ouvert sur le 3306 de la machine
  distante, `bind-address = 0.0.0.0` dans `my.cnf`, utilisateur
  `sigdep_reader` autorisé pour l'IP de l'agent (le `'%'` du SQL
  ci-dessus le fait par défaut). Dans `.env`,
  `SIGDEP_LOCAL_DB_URL=jdbc:mysql://<ip>:3306/openmrs?...`.

## B.4 — Configurer le .env

Ouvrez `.env` et complétez :

- `SIGDEP_SITE_CODE` — fourni par le hub.
- `SIGDEP_LOCAL_DB_URL` / USER / PASSWORD — selon le scénario réseau.
- `SIGDEP_CENTRAL_API_URL` — URL publique du hub.
- `SIGDEP_KEYCLOAK_URL` + `SIGDEP_KEYCLOAK_CLIENT_SECRET` — fournis
  par le hub.
- `SIGDEP_SYNC_TAG` (optionnel) — pour épingler une version précise
  au lieu de `latest`.

## B.5 — Démarrer l'agent

```bash
docker compose -f docker-compose.site.yml up -d
docker compose -f docker-compose.site.yml logs -f
```

Vous devez voir les mêmes lignes que pour le mode systemd
(`Sync cycle started`, `Enqueued X records`, `Flushed Y batch(es)`).

> **Login GHCR si l'image est privée** : si vous voyez
> `unauthorized: not authorized for ghcr.io/.../sigdep-sync`, l'image
> est privée et vous devez vous authentifier :
> ```bash
> echo "<personal_access_token>" | docker login ghcr.io -u <username> --password-stdin
> ```
> Le PAT doit avoir le scope `read:packages`. Côté hub, l'admin peut
> aussi rendre l'image publique (Packages → Settings → Change visibility).

---

# Validation finale (commune aux 2 modes)

## Valider côté hub

Demander à un administrateur du hub de vérifier que :

1. Le site apparaît dans **/app/sites** avec un statut « En ligne ».
2. La page **/app/sync** liste des batches récents pour ce site.
3. L'onglet **Rejets** ne montre pas une explosion de rejets.

Au premier cycle, l'agent peut envoyer plusieurs milliers
d'enregistrements (toute l'historique). C'est normal — laissez-le
travailler quelques heures, puis revérifiez.

## Mise à jour

### Mode A (systemd)

```bash
sudo systemctl stop sigdep-sync
sudo cp sigdep-sync-<nouvelle_version>.jar /opt/sigdep-sync/sigdep-sync.jar
sudo systemctl start sigdep-sync
sudo journalctl -u sigdep-sync -f   # vérifier le démarrage
```

### Mode B (Docker)

Si `SIGDEP_SYNC_TAG=latest` dans `.env` :

```bash
cd ~/sigdep-sync
docker compose -f docker-compose.site.yml pull
docker compose -f docker-compose.site.yml up -d
docker compose -f docker-compose.site.yml logs -f
```

Pour épingler une nouvelle version : éditer `.env`,
`SIGDEP_SYNC_TAG=1.0.2` (par exemple), puis les 3 commandes ci-dessus.

Dans les deux modes, **l'outbox SQLite et la watermark sont préservées**
(volume Docker pour le mode B, dossier `/var/lib/sigdep-agent` pour
le mode A) : l'agent reprend exactement où il en était.

## En cas de problème

### L'agent ne démarre pas

**Mode A** :
```bash
sudo journalctl -u sigdep-sync --no-pager | tail -50
```

Causes fréquentes :
- **Java pas installé** : `which java` ; sinon `apt install openjdk-17-jre-headless`.
- **Fichier env non lisible** par l'utilisateur `sigdep-agent` : vérifier les
  permissions.
- **Buffer non accessible** : vérifier `/var/lib/sigdep-agent` et le `chown`.

**Mode B** :
```bash
docker compose -f docker-compose.site.yml logs --tail=100
```

Causes fréquentes :
- **Image non accessible** (`unauthorized`) : voir la note GHCR privé
  dans la section B.5.
- **`.env` malformé** : pas de guillemets autour des valeurs, pas
  d'espaces autour des `=`.
- **Volume non monté** : vérifier `docker volume ls | grep sigdep`.

### L'agent démarre mais n'envoie rien

- **Vérifier la connectivité hub** : `curl -fsSL <SIGDEP_CENTRAL_API_URL>/actuator/health`.
- **Vérifier Keycloak** : `curl -fsSL <SIGDEP_KEYCLOAK_URL>/realms/sigdep`.
- **Vérifier MySQL local** :
  - Mode A : `mysql -u sigdep_reader -p openmrs -e 'SELECT 1'`.
  - Mode B : `docker exec sigdep-sync sh -c "echo 'SELECT 1' | mysql -u $SIGDEP_LOCAL_DB_USER ..."`
    (rare ; en pratique, l'erreur de connexion apparaît dans les
    premières lignes des logs container).

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
