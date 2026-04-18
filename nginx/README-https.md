# Passerelle HTTPS (local)

Conforme PRD : Nginx + certificat (auto-signé ou outil type mkcert).

## Démarrage rapide (recommandé)

À la **racine du dépôt** :

**Linux / macOS / Git Bash**

```bash
bash scripts/up-https.sh
```

**Windows (PowerShell)**

```powershell
.\scripts\up-https.ps1
```

Ces scripts :

1. Créent `nginx/certs/server.crt` et `nginx/certs/server.key` s’ils n’existent pas (`scripts/generate-https-certs.*`).
2. Lancent `docker compose -f docker-compose.yml -f docker-compose.https.yml up --build` (le fichier `docker-compose.https.yml` impose **`VITE_API_URL` vide** au build du frontend et un **CORS par défaut** incluant `https://localhost`).

Mode détaché : `bash scripts/up-https.sh -d` ou `.\scripts\up-https.ps1 -d`.

## Certificats seuls (OpenSSL)

Si vous préférez ne pas utiliser les scripts `up-https` :

```bash
bash scripts/generate-https-certs.sh
```

Sous Windows PowerShell :

```powershell
.\scripts\generate-https-certs.ps1
```

Sous **Windows / PowerShell**, le script cherche d’abord `openssl` dans le PATH, puis `…\Git\usr\bin\openssl.exe` (installation typique de **Git for Windows**). Si rien n’est trouvé, il tente **Docker** (image `alpine`, installation d’OpenSSL dans le conteneur). Vous pouvez aussi ajouter manuellement `C:\Program Files\Git\usr\bin` au PATH.

Ne commitez pas `server.key` / `server.crt` (voir `nginx/certs/.gitignore`).

## Dépannage : `openssl.cnf` introuvable (PostgreSQL / ODBC)

Si OpenSSL affiche une erreur du type `Can't open ...\PostgreSQL\...\openssl.cnf`, une variable **`OPENSSL_CONF`** (système ou utilisateur) pointe vers un fichier qui n’existe plus. Ouvrez *Variables d’environnement* Windows, supprimez ou corrigez **`OPENSSL_CONF`**, fermez le terminal et relancez `scripts/generate-https-certs.ps1`. Les scripts du dépôt ignorent désormais une valeur invalide et utilisent la config fournie avec Git for Windows quand c’est possible.

## Variables d’environnement

| Variable | Rôle |
|----------|------|
| `CORS_ALLOWED_ORIGINS` | Liste des origines autorisées côté backend. Avec le profil HTTPS, le défaut dans `docker-compose.https.yml` inclut `https://localhost` ; votre `.env` peut **remplacer** toute la liste si besoin. |
| `HTTPS_GATEWAY_PORT` | Port d’écoute de la passerelle (défaut **443**). Sur Windows, le port 443 peut nécessiter des droits élevés : utilisez par ex. `HTTPS_GATEWAY_PORT=8443` dans `.env`. |

Pour le profil HTTPS, **`VITE_API_URL` est forcé vide** au build par `docker-compose.https.yml` : le navigateur appelle `/api/...` sur le **même hôte** que la page (ex. `https://localhost`).

## Commande manuelle équivalente

```bash
bash scripts/generate-https-certs.sh
docker compose -f docker-compose.yml -f docker-compose.https.yml up --build
```

## Sans passerelle HTTPS

Le `docker-compose.yml` de base expose le backend (`8080`) et le frontend (`FRONTEND_PUBLISHED_PORT`, ex. `13100`) en **HTTP** ; aucun certificat ni `gateway` requis.
