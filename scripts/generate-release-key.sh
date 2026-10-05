#!/usr/bin/env bash
# Generates the private release keystore for New World Sketchware and prints the values to store as
# GitHub Actions secrets. Run it on YOUR machine, never in CI and never in a shared session.
#
# The keystore is the identity of the app: every future update must be signed with the same key.
# If you lose it (or its passwords) installed users can no longer update. Back it up offline.
set -euo pipefail

OUT="${1:-$HOME/new-world-sketchware-release.jks}"
ALIAS="release"

if [ -e "$OUT" ]; then
  echo "Refusing to overwrite existing file: $OUT" >&2
  exit 1
fi
command -v keytool >/dev/null || { echo "keytool not found, install a JDK first." >&2; exit 1; }

STORE_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)"
KEY_PASSWORD="$STORE_PASSWORD"

keytool -genkeypair -v \
  -keystore "$OUT" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" \
  -dname "CN=New World Sketchware, O=NWS, C=FR"

chmod 600 "$OUT"
echo
echo "Keystore created: $OUT"
echo "BACK IT UP NOW (password manager + offline copy). Do not commit it."
echo
echo "Add these repository secrets (GitHub > Settings > Secrets and variables > Actions):"
echo "  RELEASE_KEYSTORE_BASE64 = $(base64 < "$OUT" | tr -d '\n')"
echo "  RELEASE_STORE_PASSWORD  = $STORE_PASSWORD"
echo "  RELEASE_KEY_ALIAS       = $ALIAS"
echo "  RELEASE_KEY_PASSWORD    = $KEY_PASSWORD"
