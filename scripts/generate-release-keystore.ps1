# ==============================================================================
# Nchord — Permanent Android Release Keystore Generator (Windows PowerShell)
# ==============================================================================
# Generates a permanent 4096-bit RSA PKCS12 keystore matching app/build.gradle.kts
# (keyAlias = "upload") and prints the exact 3 GitHub Actions secrets required
# for seamless in-place Android APK updates.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\generate-release-keystore.ps1
# ==============================================================================

$ErrorActionPreference = "Stop"
$KeystoreFile = "nchord-upload-key.jks"
$KeyAlias = "upload"

if (Test-Path $KeystoreFile) {
    Write-Error "'$KeystoreFile' already exists! Back it up safely before generating a new key."
    exit 1
}

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    Write-Error "'keytool' not found on PATH. Install JDK 17+ or use Android Studio's jbr\bin\keytool.exe."
    exit 1
}

$Chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
$Rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$Bytes = New-Object byte[] 24
$Rng.GetBytes($Bytes)
$GeneratedPass = -join ($Bytes | ForEach-Object { $Chars[$_ % $Chars.Length] })

Write-Host "Generating permanent 4096-bit RSA release keystore ($KeystoreFile, alias='$KeyAlias')..." -ForegroundColor Cyan

& keytool -genkeypair `
    -v `
    -keystore $KeystoreFile `
    -storetype PKCS12 `
    -alias $KeyAlias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -storepass $GeneratedPass `
    -keypass $GeneratedPass `
    -dname "CN=Nchord, OU=Android Release, O=Nchord, L=City, ST=State, C=US"

$KeystoreBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $KeystoreFile))
$KeystoreB64 = [Convert]::ToBase64String($KeystoreBytes)

Write-Host ""
Write-Host "==============================================================================" -ForegroundColor Green
Write-Host " PERMANENT SIGNING KEY CREATED: $KeystoreFile" -ForegroundColor Green
Write-Host " Back up '$KeystoreFile' + password in your password manager! Never commit it." -ForegroundColor Yellow
Write-Host "==============================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Add these 3 Repository Secrets in GitHub (Settings -> Secrets and variables -> Actions):"
Write-Host ""
Write-Host "1) STORE_PASSWORD          = $GeneratedPass"
Write-Host "2) KEY_PASSWORD            = $GeneratedPass"
Write-Host "3) RELEASE_KEYSTORE_BASE64 ="
Write-Host $KeystoreB64
Write-Host ""
