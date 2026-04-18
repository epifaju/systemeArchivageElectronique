#Requires -Version 5.1
<#
  Génère des certificats auto-signés pour la passerelle HTTPS locale.
  Corrige OPENSSL_CONF invalide (ex. PostgreSQL ODBC pointant vers un fichier absent).
  Usage : à la racine du dépôt —  .\scripts\generate-https-certs.ps1
#>
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$CertDir = Join-Path $Root "nginx\certs"
$key = Join-Path $CertDir "server.key"
$crt = Join-Path $CertDir "server.crt"

New-Item -ItemType Directory -Force -Path $CertDir | Out-Null

if ((Test-Path $key) -and (Test-Path $crt)) {
    Write-Host "Certificats déjà présents : $crt"
    Write-Host "Supprimez-les pour en régénérer."
    exit 0
}

function Get-OpenSslExe {
    $cmd = Get-Command openssl -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $pf86 = [Environment]::GetEnvironmentVariable("ProgramFiles(x86)")
    $candidates = @(
        (Join-Path $env:ProgramFiles "Git\usr\bin\openssl.exe"),
        $(if ($pf86) { Join-Path $pf86 "Git\usr\bin\openssl.exe" }),
        (Join-Path $env:LOCALAPPDATA "Programs\Git\usr\bin\openssl.exe")
    )
    foreach ($p in $candidates) {
        if ($p -and (Test-Path -LiteralPath $p)) { return $p }
    }
    return $null
}

function Get-GitOpenSslCnf {
    foreach ($rel in @("Git\usr\ssl\openssl.cnf", "Git\mingw64\etc\ssl\openssl.cnf")) {
        $p = Join-Path $env:ProgramFiles $rel
        if (Test-Path -LiteralPath $p) { return $p }
    }
    return $null
}

function New-CertsWithDocker {
    $certDirAbs = (Resolve-Path $CertDir).Path
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) { return $false }
    Write-Host "Secours : génération des certificats via Docker (Alpine + openssl)…"
    $shCmd = "apk add --no-cache openssl >/dev/null && openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /certs/server.key -out /certs/server.crt -subj '/CN=localhost'"
    & docker run --rm -v "${certDirAbs}:/certs" alpine:3.20 sh -c $shCmd
    return ($LASTEXITCODE -eq 0)
}

$ok = $false
$opensslExe = Get-OpenSslExe
if ($opensslExe) {
    Write-Host "Utilisation de : $opensslExe"
    if ($env:OPENSSL_CONF -and -not (Test-Path -LiteralPath $env:OPENSSL_CONF)) {
        Write-Warning "OPENSSL_CONF pointe vers un fichier absent ($($env:OPENSSL_CONF)). Variable ignorée pour cette commande (voir README-https.md)."
        Remove-Item Env:\OPENSSL_CONF -ErrorAction SilentlyContinue
    }
    $gitCnf = Get-GitOpenSslCnf
    if ($gitCnf) {
        Write-Host "Fichier de config OpenSSL : $gitCnf"
        & $opensslExe req -config $gitCnf -x509 -nodes -days 365 -newkey rsa:2048 -keyout $key -out $crt -subj "/CN=localhost"
    }
    else {
        & $opensslExe req -x509 -nodes -days 365 -newkey rsa:2048 -keyout $key -out $crt -subj "/CN=localhost"
    }
    $ok = ($LASTEXITCODE -eq 0)
}

if (-not $ok -or -not (Test-Path $crt) -or -not (Test-Path $key)) {
    if (New-CertsWithDocker) {
        $ok = $true
    }
}

if (-not (Test-Path $crt) -or -not (Test-Path $key)) {
    Write-Error @"
Impossible de créer server.crt / server.key dans nginx\certs.

Si OpenSSL signale un fichier openssl.cnf manquant : supprimez ou corrigez la variable d'environnement
OPENSSL_CONF (souvent un ancien chemin PostgreSQL / ODBC). Puis relancez ce script.

Consultez nginx\README-https.md (section dépannage).
"@
    exit 1
}

Write-Host "Créé : $crt"
Write-Host "Créé : $key"
exit 0
