#Requires -Version 5.1
<#
  Démarre le projet en HTTPS via Docker (Nginx 443 → frontend + backend /api).

  Prérequis : fichier .env à la racine (copie de .env.example). Pour CORS, inclure
  https://localhost dans CORS_ALLOWED_ORIGINS si vous utilisez cette URL.

  Exemples :
    .\scripts\up-https.ps1                    # premier plan (logs)
    .\scripts\up-https.ps1 -Detached          # arrière-plan
    .\scripts\up-https.ps1 -d                 # idem (-d est un alias de -Detached)
    .\scripts\up-https.ps1 -Detached --force-recreate

  Puis ouvrir : https://localhost  (accepter l’avertissement certificat auto-signé)
  Port : HTTPS_GATEWAY_PORT dans .env (défaut 443).

  Tout argument supplémentaire est passé à : docker compose ... up --build
#>
param(
    [Alias('d')]
    [switch]$Detached,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ComposeArgs
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

& "$PSScriptRoot\generate-https-certs.ps1"

$up = @('compose', '-f', 'docker-compose.yml', '-f', 'docker-compose.https.yml', 'up', '--build')
if ($Detached) { $up += '-d' }
if ($ComposeArgs) { $up += $ComposeArgs }
& docker @up
