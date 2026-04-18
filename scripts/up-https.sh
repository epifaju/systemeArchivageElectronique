#!/usr/bin/env sh
# Démarre le projet en HTTPS avec Docker (Nginx → frontend + /api).
# Prérequis : .env à la racine ; CORS : inclure https://localhost dans CORS_ALLOWED_ORIGINS.
# Usage :
#   bash scripts/up-https.sh              # premier plan
#   bash scripts/up-https.sh -d           # arrière-plan (docker compose up -d)
#   bash scripts/up-https.sh --force-recreate
# Puis : https://localhost  (certificat auto-signé — accepter l’exception navigateur)

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

sh scripts/generate-https-certs.sh

exec docker compose -f docker-compose.yml -f docker-compose.https.yml up --build "$@"
