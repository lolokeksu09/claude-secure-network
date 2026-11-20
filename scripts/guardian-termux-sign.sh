#!/usr/bin/env bash
# Sign reviewed UNSIGNED Guardian release with the private Termux-owned key.
# Never echo or store signing passwords. Fail closed on certificate mismatch.
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
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cert_reader="$script_dir/guardian-apk-cert.py"
if [[ ! -f "$cert_reader" && -f "$HOME/guardian-apk-cert.py" ]]; then
  cert_reader="$HOME/guardian-apk-cert.py"
fi

[[ -f "$input" ]] || { echo "Input APK not found: $input" >&2; exit 2; }
[[ -f "$key_file" ]] || { echo "Missing signing key; keep any existing key unchanged." >&2; exit 2; }
[[ -f "$expected_file" ]] || { echo "Missing saved certificate fingerprint." >&2; exit 2; }
[[ -f "$cert_reader" ]] || {
  echo "Missing guardian-apk-cert.py. Download it from the same repository into $HOME." >&2
  exit 2
}
[[ ! -e "$output" ]] || {
  echo "Output already exists; refusing to overwrite: $output" >&2
  exit 2
}
command -v apksigner >/dev/null 2>&1 || { echo "apksigner not found; pkg install apksigner" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 not found; pkg install python" >&2; exit 2; }

expected=$(tr -d ':[:space:]' < "$expected_file" | tr '[:upper:]' '[:lower:]')
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Malformed saved certificate fingerprint; refusing to sign." >&2
  exit 2
}

tmp=$(mktemp "${output}.tmp.XXXXXXXX.apk")
trap 'rm -f -- "$tmp"' EXIT

# Production Termux signs interactively. CI may set an ephemeral test password in
# an environment variable, never as a command-line argument.
password_option=()
if [[ -n "${CSN_SIGNING_STORE_PASSWORD:-}" ]]; then
  password_option=(--ks-pass env:CSN_SIGNING_STORE_PASSWORD)
fi

echo "Signing with existing local key (enter its password if prompted)..."
apksigner sign --ks "$key_file" --ks-type PKCS12 \
  --ks-key-alias csn-guardian "${password_option[@]}" --out "$tmp" "$input"
apksigner verify --min-sdk-version 26 "$tmp" >/dev/null

# Parse the actual certificate bytes in the APK's v2/v3 signing block.
# Unlike the former apksigner stdout grep, this is not affected by CLI
# indentation, locale, or output formatting differences on Termux.
actual=$(python3 "$cert_reader" "$tmp")
actual=$(printf '%s' "$actual" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
if [[ ! "$actual" =~ ^[0-9a-f]{64}$ ]]; then
  echo "FAIL: cannot read a valid v2/v3 signing certificate from the signed APK." >&2
  exit 1
fi
if [[ "$actual" != "$expected" ]]; then
  echo "FAIL: signed APK certificate differs from saved signing-key fingerprint." >&2
  echo "Saved expected SHA-256: $expected" >&2
  echo "Actual APK SHA-256:    $actual" >&2
  echo "Do NOT regenerate or replace the existing signing key." >&2
  echo "Inspect the existing .p12 certificate using keytool -list -v." >&2
  exit 1
fi

mv -- "$tmp" "$output"
echo "PASS: signed APK certificate SHA256 $actual"
echo "APK: $output"
echo "CAUTION: different from existing User 11 signing key; cannot install over it."
