# Démarrage du projet en HTTPS (Docker)

Les scripts génèrent les certificats dans `nginx\certs` s’ils manquent, puis lancent la stack avec la passerelle Nginx (HTTPS → frontend + `/api` vers le backend).

## Prérequis

- Fichier **`.env`** à la racine du dépôt (copie de `.env.example`).
- Pour le navigateur sur **`https://localhost`**, la variable **`CORS_ALLOWED_ORIGINS`** doit inclure **`https://localhost`** (ou l’URL exacte + port utilisé dans la barre d’adresse).

## Windows (PowerShell)

À la racine du dépôt :

```powershell
.\scripts\up-https.ps1
```

Équivalent Docker : `docker compose -f docker-compose.yml -f docker-compose.https.yml up --build`

### En arrière-plan (conteneurs détachés)

```powershell
.\scripts\up-https.ps1 -Detached
# ou
.\scripts\up-https.ps1 -d
```

### Options supplémentaires (transmises à `docker compose`)

```powershell
.\scripts\up-https.ps1 -Detached --force-recreate
```

## Linux / macOS / Git Bash

```bash
bash scripts/up-https.sh
```

En arrière-plan :

```bash
bash scripts/up-https.sh -d
```

## Après le démarrage

1. Ouvrir **`https://localhost`** dans le navigateur (ou le port défini par **`HTTPS_GATEWAY_PORT`** dans `.env` ; par défaut **443**).
2. Accepter l’avertissement du certificat **auto-signé** (développement local).

## Rappel des variables utiles

| Variable | Rôle |
|----------|------|
| **`HTTPS_GATEWAY_PORT`** | Port d’écoute de Nginx sur l’hôte (défaut **443** si omis). |
| **`CORS_ALLOWED_ORIGINS`** | Doit contenir l’origine exacte utilisée dans le navigateur (ex. `https://localhost`). |

Pour plus de détail sur les certificats et le dépannage OpenSSL, voir **`nginx/README-https.md`**.
