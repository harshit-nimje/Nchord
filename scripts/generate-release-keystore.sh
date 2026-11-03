#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Nchord — Permanent Android Release Keystore Generator (Linux / macOS / WSL)
# ==============================================================================
# Generates a permanent 4096-bit RSA PKCS12 keystore matching app/build.gradle.kts
# (keyAlias = "upload") and outputs the exact 3 GitHub Actions secrets required
# so every future APK update installs cleanly in-place without data loss.
#
# Usage:
#   bash scripts/generate-release-keystore.sh
#   bash scripts/generate-release-keystore.sh --push-secrets owner/repo
# ==============================================================================

KEYSTORE_FILE="nchord-upload-key.jks"
KEY_ALIAS="upload"

if [ -f "$KEYSTORE_FILE" ]; then
  echo "Error: '$KEYSTORE_FILE' already exists in the current directory."
  echo "Back it up safely before generating a new key — replacing a published key breaks in-place Android updates!"
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "Error: 'keytool' not found. Install JDK 17+ (e.g. Temurin / Android Studio JBR) and ensure keytool is on PATH."
  exit 1
fi

# Generate strong random 24-character password (used for both PKCS12 store & key alias 'upload')
if command -v openssl >/dev/null 2>&1; then
  GENERATED_PASS="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)"
else
  GENERATED_PASS="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)"
fi

echo "Generating permanent 4096-bit RSA release keystore ($KEYSTORE_FILE, alias='$KEY_ALIAS')..."
keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_FILE" \
  -storetype PKCS12 \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$GENERATED_PASS" \
  -keypass "$GENERATED_PASS" \
  -dname "CN=Nchord, OU=Android Release, O=Nchord, L=City, ST=State, C=US"

KEYSTORE_B64="$(base64 < "$KEYSTORE_FILE" | tr -d '[:space:]')"

echo ""
echo "=============================================================================="
echo " PERMANENT SIGNING KEY CREATED: $KEYSTORE_FILE"
echo " IMPORTANT: Back up '$KEYSTORE_FILE' + password in your password manager!"
echo "            Do NOT commit '$KEYSTORE_FILE' to git (it is gitignored)."
echo "=============================================================================="
echo ""
echo "Certificate Fingerprints:"
keytool -list -v -keystore "$KEYSTORE_FILE" -alias "$KEY_ALIAS" -storepass "$GENERATED_PASS" | grep -E "SHA1:|SHA256:" || true
echo ""

if [ "${1:-}" = "--push-secrets" ] && [ -n "${2:-}" ]; then
  REPO="$2"
  if ! command -v gh >/dev/null 2>&1; then
    echo "Error: GitHub CLI ('gh') is not installed. Add the 3 secrets below manually in GitHub Settings."
    exit 1
  fi
  echo "Uploading permanent signing secrets to GitHub repository '$REPO'..."
  printf '%s' "$KEYSTORE_B64" | gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO"
  printf '%s' "$GENERATED_PASS" | gh secret set STORE_PASSWORD --repo "$REPO"
  printf '%s' "$GENERATED_PASS" | gh secret set KEY_PASSWORD --repo "$REPO"
  echo "Done! Permanent signing secrets uploaded to https://github.com/$REPO/settings/secrets/actions"
else
  echo "Add these 3 Repository Secrets in GitHub:"
  echo "  Repo -> Settings -> Secrets and variables -> Actions -> New repository secret"
  echo ""
  echo "1) Name:  STORE_PASSWORD"
  echo "   Value: $GENERATED_PASS"
  echo ""
  echo "2) Name:  KEY_PASSWORD"
  echo "   Value: $GENERATED_PASS"
  echo ""
  echo "3) Name:  RELEASE_KEYSTORE_BASE64"
  echo "   Value:"
  echo "$KEYSTORE_B64"
  echo ""
  echo "Tip: Or upload automatically with GitHub CLI:"
  echo "  gh secret set STORE_PASSWORD --body '$GENERATED_PASS'"
  echo "  gh secret set KEY_PASSWORD --body '$GENERATED_PASS'"
  echo "  gh secret set RELEASE_KEYSTORE_BASE64 < <(base64 '$KEYSTORE_FILE' | tr -d '[:space:]')"
fi
