#!/usr/bin/env sh
# Génère des certificats auto-signés pour la passerelle HTTPS locale (OpenSSL requis).
# Usage : depuis la racine du dépôt —  bash scripts/generate-https-certs.sh

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CERT_DIR="$ROOT/nginx/certs"
KEY="$CERT_DIR/server.key"
CRT="$CERT_DIR/server.crt"

mkdir -p "$CERT_DIR"

if [ -f "$KEY" ] && [ -f "$CRT" ]; then
  echo "Certificats déjà présents : $CRT"
  echo "Supprimez-les pour en régénérer."
  exit 0
fi

if ! command -v openssl >/dev/null 2>&1; then
  echo "Erreur : openssl est introuvable dans le PATH."
  exit 1
fi

# OPENSSL_CONF peut pointer vers un fichier supprimé (ex. outil ODBC) et faire échouer openssl.
if [ -n "${OPENSSL_CONF:-}" ] && [ ! -f "$OPENSSL_CONF" ]; then
  echo "Avertissement : OPENSSL_CONF=$OPENSSL_CONF est absent — variable ignorée pour cette commande."
  unset OPENSSL_CONF
fi

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$KEY" \
  -out "$CRT" \
  -subj "/CN=localhost"

echo "Créé : $CRT"
echo "Créé : $KEY"
