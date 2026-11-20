#!/usr/bin/env bash
# Sign a reviewed UNSIGNED Guardian release with the private Termux-owned stable key.
# No password appears in command-line arguments, repository or generated logs.
set -euo pipefail
umask 077

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: guardian-termux-sign.sh input-unsigned.apk [output-signed.apk]" >&2
  exit 2
fi
input=$1
output=${2:-"$HOME/CSN-Guardian-newkey-signed.apk"}
key_dir="$HOME/.local/share/csn-guardian/signing"
key_file="$key_dir/guardian-release.p12"
expected_file="$key_dir/certificate-sha256.txt"

[[ -f "$input" ]] || { echo "Input APK not found: $input" >&2; exit 2; }
[[ -f "$key_file" ]] || { echo "Missing signing key. Run guardian-termux-create-key.sh first." >&2; exit 2; }
[[ -f "$expected_file" ]] || { echo "Missing verified signing certificate fingerprint." >&2; exit 2; }
[[ ! -e "$output" ]] || { echo "Output already exists; refusing to overwrite: $output" >&2; exit 2; }

command -v apksigner >/dev/null 2>&1 || {
  echo "apksigner not found. In Termux: pkg install apksigner" >&2
  exit 2
}
expected=$(tr -d ':[:space:]' < "$expected_file" | tr '[:upper:]' '[:lower:]')
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Malformed expected certificate hash." >&2
  exit 2
}

tmp="${output}.tmp.$$"
trap 'rm -f -- "$tmp"' EXIT
echo "Signing with the persistent local key (password prompt is expected)..."
apksigner sign --ks "$key_file" --ks-type PKCS12 \
  --ks-key-alias csn-guardian --out "$tmp" "$input"
apksigner verify --min-sdk-version 26 "$tmp" >/dev/null
details=$(apksigner verify --print-certs "$tmp")
actual=$(printf '%s\n' "$details" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
  head -n 1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
if [[ "$actual" != "$expected" ]]; then
  echo "FAIL: the signed APK certificate does not match the saved public key fingerprint." >&2
  exit 1
fi
mv -- "$tmp" "$output"
echo "PASS: signed APK certificate SHA256 $actual"
echo "APK: $output"
echo "CAUTION: different from existing User 11 signing key; cannot install over it."
