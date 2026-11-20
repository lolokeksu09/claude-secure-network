# CSN Guardian — APK signing and safe upgrades

## Confirmed failure (20 November 2026)

The owner's Lenovo reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE` while updating
`dev.lolokeksu.csn.guardian` from `0.1.2-alpha` to `0.1.3-alpha`.
The existing managed profile is **User 11 — CSN Secure**, owned by Guardian.
Do **not** uninstall its DPC, remove its work profile, clear its data, or use
`pm uninstall` / `pm remove-user` as an update workaround.

Inspection of GitHub Actions APK signature blocks:

| Artifact | APK v2 certificate SHA-256 |
|---|---|
| Run 44711177603 (earlier debug) | `8fb6465a3b6451928f24c70af6c0d7bab3041d662ad78b71ebbf5e3c4f338062` |
| Run 44714525134 (v0.1.3 debug) | `1a80a20ab2e29b933db4374bf7a2479af7497102ef719888ac1bad32497b4338` |

A fresh GitHub runner generates an **ephemeral debug keystore**. These two certificates
are different. The installed APK's **actual** certificate has yet to be read, so
do not equate its signer to the earlier workflow artifact without checking.

## 1. Read-only audit on the Lenovo

With Shizuku authorized, run in Termux:

```sh
cd "$HOME"
./rish -c 'P=$(pm path --user 11 dev.lolokeksu.csn.guardian | sed -n "s/^package://p" | head -n 1); test -n "$P" && cp "$P" /sdcard/Download/CSN-Guardian-installed.apk'
curl -fsSL 'https://raw.githubusercontent.com/lolokeksu09/claude-secure-network/feat/csn-guardian-dpc/scripts/guardian-apk-cert.py' -o "$HOME/guardian-apk-cert.py"
python "$HOME/guardian-apk-cert.py" /storage/emulated/0/Download/CSN-Guardian-installed.apk
```

This reads the installed package's **public signing certificate**, not its private
key. The copy of the installed APK is written to Downloads and can be deleted
afterwards. Never send VPN subscription material or any keystore/private key.

Look for the old private key only on its original signing machine, backups or
local developer keystores. GitHub Actions run artifacts contain the APK but
**do not provide the CI runner's debug keystore**. Extracting the public
certificate from an installed APK cannot recover the private key.

If the original signing key is absent, a compatible ordinary APK update is
not possible; Android key rotation also requires proof from the old private
key. Any migration that destroys or replaces User 11 requires a **separate**
explicit user decision and backup/restore strategy.

## 2. Prepare permanent signing for future profiles/installs

The private keystore must remain outside Git and any chat messages. On a trusted
machine, generate a unique signing keystore and make a secure offline backup:

```sh
keytool -genkeypair -keystore csn-guardian-release.jks -storetype PKCS12 \
  -alias csn-guardian -keyalg RSA -keysize 3072 -validity 10000
keytool -list -v -keystore csn-guardian-release.jks -alias csn-guardian
```

The SHA-256 in `keytool -list` is the **public certificate fingerprint**.
Store the keystore and passwords in a password manager / encrypted backup;
never put them in the public repository.

Configure a protected GitHub Actions Environment named `guardian-signing`
with reviewer controls. Put these **environment secrets** there:
`CSN_SIGNING_KEYSTORE_BASE64` (base64 of full keystore file),
`CSN_SIGNING_ALIAS`, `CSN_SIGNING_STORE_PASSWORD`,
`CSN_SIGNING_KEY_PASSWORD`. Set environment **variable**
`CSN_EXPECTED_CERT_SHA256` to the expected SHA-256 certificate fingerprint.

Workflow `.github/workflows/guardian-signed.yml` builds and signs only upon
manual `workflow_dispatch` of reviewed `main`, and refuses an incorrect
signing fingerprint. The workflow is in the feature PR and will not be
available for manual dispatch until accepted on the default branch. It
does not deploy to the phone automatically. Debug builds remain for CI,
**not for upgrades to an installed Profile Owner**.

Before producing an APK intended to upgrade the current Guardian installation,
confirm that the keystore used here matches the **installed** signing
certificate. A newly created keystore does *not* retroactively fix User 11.
Do not merge or deploy a change claiming the migration succeeded without
on-device installation and Profile Owner read-back.

## 3. Evidence required to close the issue

1. Record the installed Guardian certificate fingerprint from User 11.
2. Identify an available private signing key with the matching certificate.
3. Sign an APK with that key, validate it with `apksigner verify --print-certs`.
4. Install without removing User 11 and confirm version and unchanged Profile Owner.
5. Verify image-sharing fix and re-run VPN policy safety checks.
6. Store one stable signing process and backed-up key for every future build.

Steps 1–5 remain **NOT RUN** on the Lenovo. No key has been retrieved or
generated in this repository.
