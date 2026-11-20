#!/usr/bin/env bash
# CI-only end-to-end check with a disposable key. No user signing credentials.
set -euo pipefail
umask 077

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: test-guardian-termux-sign.sh path/to/unsigned-release.apk" >&2
  exit 2
fi
input=$(realpath "$1")
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
export HOME="$work/home"
mkdir -p "$HOME/.local/share/csn-guardian/signing"
key_file="$HOME/.local/share/csn-guardian/signing/guardian-release.p12"
expected_file="$HOME/.local/share/csn-guardian/signing/certificate-sha256.txt"
# All credentials below are disposable test-only values with no deployment use.
export CSN_SIGNING_STORE_PASSWORD='csn-ci-ephemeral-key-only-2026'
keytool -genkeypair -noprompt -storetype PKCS12 \
  -keystore "$key_file" -alias csn-guardian \
  -storepass "$CSN_SIGNING_STORE_PASSWORD" \
  -keypass "$CSN_SIGNING_STORE_PASSWORD" \
  -keyalg RSA -keysize 3072 -validity 30 \
  -dname 'CN=Ephemeral Guardian CI Test' >/dev/null 2>&1
digest=$(keytool -list -v -storetype PKCS12 \
  -keystore "$key_file" -alias csn-guardian \
  -storepass "$CSN_SIGNING_STORE_PASSWORD" |
  sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1 |
  tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
[[ "$digest" =~ ^[0-9a-f]{64}$ ]] || { echo "Test key cert fingerprint invalid" >&2; exit 1; }
printf '%s\n' "$digest" > "$expected_file"

echo "TEST 1: temporary valid key => fully verified signed APK"
bash "$root/scripts/guardian-termux-sign.sh" "$input" "$work/valid.apk"
[[ -f "$work/valid.apk" ]]
apk_digest=$(python3 "$root/scripts/guardian-apk-cert.py" "$work/valid.apk")
[[ "$apk_digest" == "$digest" ]] || {
  echo "FAIL: signed APK certificate differs from ephemeral test key" >&2
  exit 1
}
apksigner verify --min-sdk-version 26 "$work/valid.apk" >/dev/null

echo "TEST 2: stale expected digest => hard failure without output APK"
printf '%064d\n' 0 > "$expected_file"
if bash "$root/scripts/guardian-termux-sign.sh" "$input" "$work/stale.apk"; then
  echo "FAIL: stale expected certificate was accepted" >&2
  exit 1
fi
[[ ! -e "$work/stale.apk" ]] || {
  echo "FAIL: a mismatched APK was published" >&2
  exit 1
}

echo "TEST 3: existing output => refuse overwrite"
printf '%s\n' "$digest" > "$expected_file"
if bash "$root/scripts/guardian-termux-sign.sh" "$input" "$work/valid.apk"; then
  echo "FAIL: existing signed APK overwritten" >&2
  exit 1
fi

echo "PASS: local signer verified against real disposable PKCS12 key, signed APK, and two negative cases."
