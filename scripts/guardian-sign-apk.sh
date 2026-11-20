#!/usr/bin/env bash
# Sign a Guardian APK with a persistent keystore. Fail closed on a signer mismatch.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: guardian-sign-apk.sh input-unsigned.apk output-signed.apk" >&2
  exit 2
fi
input=$1
output=$2
[[ -f "$input" ]] || { echo "Input APK not found" >&2; exit 2; }
[[ ! -e "$output" ]] || { echo "Output already exists; not overwriting" >&2; exit 2; }

for variable in CSN_SIGNING_KEYSTORE CSN_SIGNING_ALIAS CSN_SIGNING_STORE_PASSWORD CSN_SIGNING_KEY_PASSWORD CSN_EXPECTED_CERT_SHA256; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Missing required signing setting: $variable" >&2
    exit 2
  fi
done
[[ -f "$CSN_SIGNING_KEYSTORE" ]] || { echo "Signing keystore not found" >&2; exit 2; }

if command -v apksigner >/dev/null 2>&1; then
  signer=$(command -v apksigner)
else
  signer=""
  for candidate in "${ANDROID_HOME:-/usr/local/lib/android/sdk}"/build-tools/*/apksigner; do
    if [[ -x "$candidate" ]]; then signer=$candidate; fi
  done
  [[ -n "$signer" ]] || { echo "apksigner not found in Android SDK" >&2; exit 2; }
fi

expected=$(printf '%s' "$CSN_EXPECTED_CERT_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo "Invalid expected SHA-256 certificate fingerprint" >&2; exit 2; }

tmp=$(mktemp "${output}.tmp.XXXXXXXX.apk")
trap 'rm -f -- "$tmp"' EXIT

"$signer" sign --ks "$CSN_SIGNING_KEYSTORE" \
  --ks-key-alias "$CSN_SIGNING_ALIAS" \
  --ks-pass env:CSN_SIGNING_STORE_PASSWORD \
  --key-pass env:CSN_SIGNING_KEY_PASSWORD \
  --out "$tmp" "$input"
"$signer" verify --min-sdk-version 26 "$tmp" >/dev/null
details=$("$signer" verify --print-certs "$tmp")
actual=$(printf '%s\n' "$details" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1 | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')
if [[ -z "$actual" || "$actual" != "$expected" ]]; then
  echo "REFUSED: signing certificate does not match expected fingerprint" >&2
  echo "Expected: $expected" >&2
  echo "Actual:   ${actual:-unavailable}" >&2
  exit 1
fi
mv -- "$tmp" "$output"
echo "Signed APK verified. Certificate SHA-256: $actual"
echo "APK: $output"
