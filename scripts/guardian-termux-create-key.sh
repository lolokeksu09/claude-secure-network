#!/usr/bin/env bash
# Generate a stable CSN Guardian signing key ON THE USER'S TERMUX DEVICE.
# Never send this keystore, its passphrase, or its base64 to chats or repositories.
set -euo pipefail
umask 077

command -v keytool >/dev/null 2>&1 || {
  echo "keytool not found. Install in Termux: pkg install openjdk-21" >&2
  exit 2
}
key_dir="${HOME}/.local/share/csn-guardian/signing"
key_file="${key_dir}/guardian-release.p12"
fingerprint_file="${key_dir}/certificate-sha256.txt"
mkdir -p -- "$key_dir"
chmod 700 -- "$key_dir"

if [[ -e "$key_file" ]]; then
  echo "Existing signing keystore detected; not replacing it: $key_file" >&2
  echo "To check its certificate, use: keytool -list -v -keystore '$key_file' -alias csn-guardian" >&2
  exit 3
fi

echo "Generating a PRIVATE PKCS12 keystore in Termux."
echo "Enter a new STRONG password and save it in a password manager."
echo "The password is never saved in this script or sent over the network."
echo "This key CANNOT update the existing User 11 install signed by the lost CI key."
keytool -genkeypair \
  -keystore "$key_file" -storetype PKCS12 \
  -alias csn-guardian -keyalg RSA -keysize 3072 -validity 10000 \
  -dname "CN=CSN Guardian, OU=Personal Signing" || {
    echo "Keytool failed. Inspect local key file before trying again." >&2
    exit 1
  }
chmod 600 -- "$key_file"
echo "Read the signing certificate's public SHA256 fingerprint (password prompt):"
details=$(keytool -list -v -keystore "$key_file" -alias csn-guardian)
fingerprint=$(printf '%s\n' "$details" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
if [[ ! "$fingerprint" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Cannot verify certificate fingerprint; do not use this key yet." >&2
  exit 1
fi
printf '%s\n' "$fingerprint" > "$fingerprint_file"
chmod 600 -- "$fingerprint_file"
echo "CREATED: $key_file"
echo "PUBLIC CERT SHA256: $fingerprint"
echo "BACKUP REQUIRED: copy the password-protected .p12 to offline storage."
echo "A backup without its password cannot restore your signing identity."
echo "Do not delete or migrate Android User 11 yet."
