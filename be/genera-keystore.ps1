# =====================================================================
#  Rigenera il keystore self-signed per l'HTTPS in sviluppo.
#
#  Crea src/main/resources/server-keystore.p12 con un certificato
#  valido per localhost (SAN: DNS:localhost, IP:127.0.0.1).
#  Il keystore e' ignorato da git: ogni sviluppatore lo rigenera in locale.
#
#  Uso:
#    .\genera-keystore.ps1                    # password default "changeit"
#    .\genera-keystore.ps1 -Password "altra"  # password personalizzata
#
#  NB: la password deve combaciare con KEYSTORE_PASSWORD nel file .env
#      (o con il default in application.properties: quarkus.tls...password).
# =====================================================================
param(
    [string]$Password = "changeit",
    [string]$Alias    = "gestionale",
    [int]$Giorni      = 825
)

$ErrorActionPreference = "Stop"

# Percorso di destinazione del keystore (relativo allo script)
$resourcesDir = Join-Path $PSScriptRoot "src\main\resources"
$keystore     = Join-Path $resourcesDir "server-keystore.p12"

# --- Trova keytool: prima sul PATH, poi tramite JAVA_HOME ---
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $candidate) { $keytool = $candidate }
}
if (-not $keytool) {
    Write-Error "keytool non trovato. Assicurati che un JDK sia sul PATH o che JAVA_HOME sia impostata."
    exit 1
}
Write-Host "keytool: $keytool"

# --- Rimuove il keystore esistente, se presente ---
if (Test-Path $keystore) {
    Write-Host "Rimuovo il keystore esistente..."
    Remove-Item $keystore -Force
}

# --- Genera il nuovo keystore ---
& $keytool -genkeypair `
    -alias $Alias `
    -keyalg RSA -keysize 2048 `
    -storetype PKCS12 `
    -keystore $keystore `
    -storepass $Password -keypass $Password `
    -validity $Giorni `
    -dname "CN=localhost, OU=Dev, O=Gestionale, C=IT" `
    -ext "SAN=DNS:localhost,IP:127.0.0.1"

if ($LASTEXITCODE -eq 0) {
    $info = Get-Item $keystore
    Write-Host ""
    Write-Host "OK - keystore creato: $($info.FullName) ($($info.Length) byte)" -ForegroundColor Green
    Write-Host "Ricorda: la password e' '$Password' (deve combaciare con KEYSTORE_PASSWORD nel .env)."
} else {
    Write-Error "Generazione del keystore fallita (exit code $LASTEXITCODE)."
    exit 1
}
