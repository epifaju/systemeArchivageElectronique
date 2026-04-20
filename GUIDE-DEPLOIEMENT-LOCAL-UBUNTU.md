# Guide de déploiement local sur Ubuntu

Ce document décrit comment installer et faire tourner **l’application d’archivage électronique** sur une machine **Ubuntu** (PC fixe ou portable), en utilisant **Docker** et **Docker Compose**, comme prévu par le dépôt.

**Public** : développeur ou administrateur système familier avec le terminal.  
**Durée indicative** : 30 à 60 minutes (selon connexion et expérience Docker).

---

## Table des matières

1. [Vue d’ensemble](#1-vue-densemble)
2. [Prérequis](#2-prérequis)
3. [Installation de Docker sur Ubuntu](#3-installation-de-docker-sur-ubuntu)
4. [Récupération du code](#4-récupération-du-code)
5. [Configuration `.env`](#5-configuration-env)
6. [Répertoires de données](#6-répertoires-de-données)
7. [Démarrage en HTTP (sans passerelle HTTPS)](#7-démarrage-en-http-sans-passerelle-https)
8. [Démarrage en HTTPS (recommandé pour coller au PRD)](#8-démarrage-en-https-recommandé-pour-coller-au-prd)
9. [Accès à l’application et compte initial](#9-accès-à-lapplication-et-compte-initial)
10. [Commandes courantes (logs, arrêt, rebuild)](#10-commandes-courantes-logs-arrêt-rebuild)
11. [Mise à jour du code](#11-mise-à-jour-du-code)
12. [Dépannage](#12-dépannage)
13. [Documentation associée dans le dépôt](#13-documentation-associée-dans-le-dépôt)

---

## 1. Vue d’ensemble

L’application est composée de :

| Composant | Rôle |
|-----------|------|
| **PostgreSQL** | Base de données (conteneur `postgres`, données sur volume `./data/postgres`). |
| **Backend** | API Spring Boot (profil `prod` dans Docker), migrations **Flyway** au démarrage. |
| **Frontend** | Interface web (build statique servi par Nginx dans le conteneur `frontend`). |
| **ClamAV** | Analyse antivirus optionnelle des fichiers uploadés. |
| **Passerelle Nginx** | Uniquement avec le profil **HTTPS** : TLS, routage vers le frontend et `/api` vers le backend. |

Deux modes de déploiement local :

- **HTTP** : `docker-compose.yml` seul — frontend et backend exposés sur des ports distincts (`13100` et `8080` par défaut).
- **HTTPS** : `docker-compose.yml` + `docker-compose.https.yml` — une passerelle Nginx écoute en TLS (souvent le port **443**).

---

## 2. Prérequis

### 2.1 Système

- **Ubuntu** 22.04 LTS ou 24.04 LTS (recommandé).
- Accès **sudo**.
- Connexion Internet pour télécharger les images Docker et les paquets.

### 2.2 Ressources matérielles (indicatif)

| Ressource | Minimum raisonnable | Confortable si OCR actif |
|------------|---------------------|---------------------------|
| RAM | 4 Go | 8 Go ou plus |
| Disque libre | 10 Go | 20 Go+ (images + données + documents) |
| CPU | 2 cœurs | 4 cœurs |

L’**OCR** est gourmand en CPU ; la variable `OCR_WORKERS` contrôle le parallélisme côté backend (voir `.env.example`).

### 2.3 Logiciels

- **Git** (pour cloner le dépôt).
- **OpenSSL** (pour générer les certificats auto-signés du mode HTTPS — en général déjà installé : paquet `openssl`).

Installation rapide des utilitaires :

```bash
sudo apt update
sudo apt install -y git ca-certificates curl openssl
```

---

## 3. Installation de Docker sur Ubuntu

Suivez la **documentation officielle** Docker pour Ubuntu (méthode « repository ») :  
[https://docs.docker.com/engine/install/ubuntu/](https://docs.docker.com/engine/install/ubuntu/)

Résumé des étapes typiques :

1. Désinstaller d’anciennes versions éventuelles (`docker.io`, etc.) si besoin.
2. Ajouter la clé GPG et le dépôt Docker adapté à votre version d’Ubuntu.
3. Installer les paquets : `docker-ce`, `docker-ce-cli`, `containerd.io`, et le plugin **Docker Compose** (`docker-compose-plugin`).

Vérification :

```bash
docker --version
docker compose version
```

### Exécuter Docker sans `sudo` (recommandé)

```bash
sudo usermod -aG docker "$USER"
```

Puis **fermer la session** et se reconnecter (ou exécuter `newgrp docker`) pour que le groupe `docker` soit pris en compte.

Test :

```bash
docker run --rm hello-world
```

---

## 4. Récupération du code

Placez le projet où vous voulez (ex. `~/projets`) :

```bash
mkdir -p ~/projets
cd ~/projets
git clone <URL_DU_DEPOT> systemeArchivageElectronique
cd systemeArchivageElectronique
```

Remplacez `<URL_DU_DEPOT>` par l’URL HTTPS ou SSH de votre dépôt Git.

---

## 5. Configuration `.env`

À la **racine du dépôt** (même dossier que `docker-compose.yml`), Docker Compose charge automatiquement le fichier **`.env`**.

### 5.1 Création du fichier

```bash
cd ~/projets/systemeArchivageElectronique   # adaptez le chemin
cp .env.example .env
nano .env                                     # ou vim, code, etc.
```

Ne **versionnez jamais** `.env` (il est listé dans `.gitignore`).

### 5.2 Variables obligatoires à personnaliser

| Variable | Description |
|----------|-------------|
| `POSTGRES_PASSWORD` | Mot de passe du compte PostgreSQL du conteneur. **Important** : si le répertoire `./data/postgres` existe déjà avec des données, changer ce mot de passe dans `.env` **sans** réinitialiser le volume peut empêcher le backend de se connecter (le mot de passe est figé dans les fichiers du volume). Voir [§12.1](#121-postgresql-mot-de-passe-refusé-après-modification-du-env). |
| `JWT_SECRET` | Secret pour signer les JWT. Doit être **long** (ex. au moins 32 caractères pour HS256). Génération possible : `openssl rand -base64 48`. |

### 5.3 Variables souvent utiles en local

| Variable | Rôle |
|----------|------|
| `OCR_WORKERS` | Nombre de workers OCR asynchrones (défaut `2`). |
| `CLAMAV_ENABLED` | `true` ou `false` — désactiver (`false`) si vous voulez alléger la machine ou éviter d’attendre le démarrage de ClamAV. |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées par le backend. **En mode HTTPS** via la passerelle, inclure **`https://localhost`** (ou l’URL exacte + port utilisée dans le navigateur). Voir `.env.example`. |
| `VITE_API_URL` | **Mode HTTP seul** : URL du backend **vue par le navigateur** (ex. `http://localhost:8080`). **Mode HTTPS avec `docker-compose.https.yml`** : laissée **vide** au build (le compose HTTPS la force) — les appels API passent en **same-origin** `/api`. |
| `FRONTEND_PUBLISHED_PORT` | Port sur l’hôte pour accéder au frontend en **HTTP** (défaut `13100`). |
| `HTTPS_GATEWAY_PORT` | Port d’écoute de Nginx en **HTTPS** (défaut `443`). Sur certaines machines, utiliser `8443` si le port 443 pose problème. |

Les autres variables (import dossier surveillé, rate limit auth, etc.) sont documentées dans **`.env.example`**.

---

## 6. Répertoires de données

Le `docker-compose.yml` monte des volumes sur l’hôte :

| Chemin hôte | Usage |
|-------------|--------|
| `./data/postgres` | Données PostgreSQL. |
| `./data/documents` | Fichiers archivés (PDF, etc.). |
| `./data/watch-incoming` | Dossier optionnel pour l’ingestion automatique (sous-dossiers `processed/`, `failed/`, etc.). |

Création recommandée **avant** le premier `docker compose up` :

```bash
cd ~/projets/systemeArchivageElectronique
mkdir -p data/postgres data/documents data/watch-incoming
```

**Sauvegarde** : pour sauvegarder votre environnement local, copiez au minimum `data/postgres` et `data/documents`.

---

## 7. Démarrage en HTTP (sans passerelle HTTPS)

Adapté pour un test rapide ou si vous ne souhaitez pas gérer TLS localement.

### 7.1 Vérifier `.env`

- `VITE_API_URL=http://localhost:8080` (ou l’IP de la machine si accès depuis un autre poste — dans ce cas adaptez aussi `CORS_ALLOWED_ORIGINS`).

### 7.2 Lancer la stack

À la racine du dépôt :

```bash
docker compose up --build
```

La première fois, le téléchargement des images et les builds peuvent prendre plusieurs minutes.

### 7.3 Mode détaché (arrière-plan)

```bash
docker compose up -d --build
```

### 7.4 Accès

- **Interface** : [http://localhost:13100](http://localhost:13100) (sauf si vous avez changé `FRONTEND_PUBLISHED_PORT`).
- **API** (directe) : [http://localhost:8080](http://localhost:8080) (ex. actuator, swagger selon configuration).

PostgreSQL **n’est pas** publié sur l’hôte par défaut (sécurité). Le backend y accède via le réseau Docker (`postgres:5432`).

---

## 8. Démarrage en HTTPS (recommandé pour coller au PRD)

La passerelle **Nginx** termine le TLS et envoie le trafic vers le frontend ; les requêtes **`/api`** sont proxifiées vers le backend. Les certificats sont **auto-signés** (navigateur affichera un avertissement à accepter en développement).

### 8.1 Prérequis `.env`

- Inclure **`https://localhost`** dans **`CORS_ALLOWED_ORIGINS`** (liste complète des origines autorisées — le backend remplace le défaut du compose si vous définissez cette variable). Exemple dans `.env.example` :
  - `CORS_ALLOWED_ORIGINS=https://localhost,http://localhost:5173,http://localhost:3000,http://localhost:13100`

### 8.2 Script de démarrage (recommandé)

À la racine du dépôt :

```bash
bash scripts/up-https.sh
```

Ce script :

1. Exécute `scripts/generate-https-certs.sh` si les fichiers `nginx/certs/server.crt` et `nginx/certs/server.key` n’existent pas.
2. Lance `docker compose -f docker-compose.yml -f docker-compose.https.yml up --build`.

**Mode détaché** :

```bash
bash scripts/up-https.sh -d
```

### 8.3 Équivalent manuel

```bash
bash scripts/generate-https-certs.sh
docker compose -f docker-compose.yml -f docker-compose.https.yml up --build
```

### 8.4 Port de la passerelle HTTPS

- Par défaut, le fichier `docker-compose.https.yml` mappe le port **`443`** de l’hôte vers Nginx.
- Sous Ubuntu, le bind sur **443** peut parfois échouer selon la configuration réseau / permissions Docker. En cas de problème, dans **`.env`** :

```env
HTTPS_GATEWAY_PORT=8443
```

Puis relancez la stack et ouvrez **[https://localhost:8443](https://localhost:8443)** (et adaptez `CORS_ALLOWED_ORIGINS` pour inclure cette origine si nécessaire).

### 8.5 Certificats

- Génération : **OpenSSL**, CN=`localhost`, fichiers dans `nginx/certs/`.
- Pour **régénérer** : supprimez `nginx/certs/server.crt` et `nginx/certs/server.key`, puis relancez `bash scripts/generate-https-certs.sh`.

Détails : **`nginx/README-https.md`** et **`DEMARRAGE-HTTPS.md`**.

### 8.6 Variable `OPENSSL_CONF`

Si OpenSSL échoue avec un message lié à un fichier **`openssl.cnf`** introuvable (parfois après installation d’autres logiciels), vérifiez :

```bash
echo "$OPENSSL_CONF"
```

Si la variable pointe vers un fichier absent, désactivez-la pour la session ou corrigez-la. Les scripts du dépôt tentent d’ignorer une valeur invalide ; voir **`nginx/README-https.md`**.

---

## 9. Accès à l’application et compte initial

### 9.1 URL selon le mode

| Mode | URL typique |
|------|-------------|
| HTTP | `http://localhost:13100` |
| HTTPS (port par défaut) | `https://localhost` |
| HTTPS (port 8443) | `https://localhost:8443` |

Acceptez l’exception de sécurité du navigateur pour le certificat **auto-signé** en local.

### 9.2 Compte administrateur (données de seed Flyway)

Une migration (`V6__seed_data.sql`) crée un utilisateur :

| Champ | Valeur |
|-------|--------|
| **Nom d’utilisateur** | `admin` |
| **Mot de passe** | `password` |

Ce couple est prévu pour le **développement local** uniquement. **Changez le mot de passe** après la première connexion (fonctionnalité selon l’écran Paramètres / administration) et **n’utilisez jamais** ce mot de passe en production.

---

## 10. Commandes courantes (logs, arrêt, rebuild)

Toutes les commandes s’exécutent à la **racine du dépôt**.

### 10.1 HTTP

```bash
# Arrêt (conteneurs supprimés du réseau par défaut, volumes conservés)
docker compose down

# Logs de tous les services
docker compose logs -f

# Logs d’un service
docker compose logs -f backend
```

### 10.2 HTTPS

```bash
docker compose -f docker-compose.yml -f docker-compose.https.yml down
docker compose -f docker-compose.yml -f docker-compose.https.yml logs -f
docker compose -f docker-compose.yml -f docker-compose.https.yml logs -f backend
```

### 10.3 Rebuild forcé (après changement de dépendances ou de Dockerfile)

```bash
docker compose build --no-cache
docker compose up -d
```

Même principe avec les deux fichiers `-f` pour le profil HTTPS.

### 10.4 Vérifier l’état des conteneurs

```bash
docker compose ps
# ou avec le profil HTTPS :
docker compose -f docker-compose.yml -f docker-compose.https.yml ps
```

---

## 11. Mise à jour du code

```bash
cd ~/projets/systemeArchivageElectronique
git pull
docker compose build --no-cache
docker compose up -d
```

Avec HTTPS :

```bash
docker compose -f docker-compose.yml -f docker-compose.https.yml build --no-cache
docker compose -f docker-compose.yml -f docker-compose.https.yml up -d
```

Les migrations Flyway s’appliquent au **démarrage du backend** ; en cas de conflit de schéma, consultez les logs du backend.

---

## 12. Dépannage

### 12.1 PostgreSQL : mot de passe refusé après modification du `.env`

Le mot de passe PostgreSQL est initialisé **au premier démarrage** du volume `./data/postgres`. Si vous changez `POSTGRES_PASSWORD` dans `.env` alors que le volume contient déjà une instance initialisée :

- soit remettez l’**ancien** mot de passe dans `.env` ;
- soit **supprimez** `./data/postgres` (**perte de toutes les données** de la base) puis relancez `docker compose up`.

### 12.2 Le backend ne démarre pas ou redémarre en boucle

```bash
docker compose logs backend
```

Causes fréquentes : base inaccessible, variable manquante, erreur Flyway, ClamAV pas prêt (attendre quelques minutes au premier lancement ClamAV ou désactiver `CLAMAV_ENABLED=false`).

### 12.3 Erreurs CORS dans le navigateur

L’origine affichée dans la console du navigateur (schéma + hôte + port) doit être listée dans **`CORS_ALLOWED_ORIGINS`** côté backend. En HTTPS local, inclure **`https://localhost`** (avec le bon port si vous n’utilisez pas 443).

### 12.4 Port déjà utilisé

- **8080** ou **13100** : changez les mappings dans `docker-compose.yml` ou les variables `FRONTEND_PUBLISHED_PORT` / section `ports` (selon ce que vous modifiez).
- **443** : utilisez `HTTPS_GATEWAY_PORT=8443` dans `.env`.

### 12.5 Accéder à PostgreSQL depuis l’hôte (DBeaver, `psql`)

Par défaut, PostgreSQL n’expose pas le port 5432 sur l’hôte. Pour le debug local, vous pouvez créer un fichier **`docker-compose.override.yml`** à la racine (non versionné ou versionné selon votre politique) :

```yaml
services:
  postgres:
    ports:
      - "15432:5432"
```

Puis `docker compose up -d` et connexion sur `localhost:15432` avec `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` du `.env`.

### 12.6 Espace disque

Surveillez `./data/documents` (fichiers uploadés) et les images Docker (`docker system df`).

---

## 13. Documentation associée dans le dépôt

| Fichier | Contenu |
|---------|---------|
| `.env.example` | Liste commentée des variables d’environnement. |
| `DEMARRAGE-HTTPS.md` | Démarrage HTTPS (Windows / Linux). |
| `nginx/README-https.md` | Passerelle HTTPS, certificats, variables, commandes manuelles. |
| `CHECKLIST-PHASE-D-INFRA.md` | Pistes production (sauvegardes, stockage, scalabilité OCR, etc.). |
| `docker-compose.yml` | Stack de base (ports, volumes, services). |
| `docker-compose.https.yml` | Surcouche HTTPS (Nginx, CORS par défaut, `VITE_API_URL` vide au build frontend). |

---

**Fin du guide.** En cas d’évolution du dépôt (nouveaux services, nouvelles variables), mettez à jour ce fichier en parallèle des changements dans `docker-compose.yml` et `.env.example`.
