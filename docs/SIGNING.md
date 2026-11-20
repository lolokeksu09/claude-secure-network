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
are different. On 20 Nov, the Lenovo owner used the read-only APK probe and reported
`b471b54672ebed98ac3d4de547d1a4c4661c7c20b78a10df0d0337a25e5c5`.
The owner subsequently provided the **full 64-character fingerprint**:
`b471b54672ebed98ac3d4de547d1a4c4661c7c20b78a10df0d0337a25e5c55a6`.
This installed certificate differs from both archived CI APK fingerprints.
No matching private key has been located or verified. Do **not** remove User 11.

**Original build identified (20 Nov 2026, independent archive inspection):**
- GitHub Actions **run 44709849744** (run number 18), commit
  `0bffb0c8e2ddbef32d8f2cf23eb3ce0a7ee49e65`,
  artifact ID `14615604875`.
- Archived APK is **21239 bytes**, matching the byte size from Lenovo's
  `ls -l` command. Its signing certificate SHA-256 is
  `b471b54672ebed98ac3d4de547d1a4c4661c7c20b78a10df0d0337a25e5c55a6`,
  **identical to the installed certificate**.
- The archived ZIP contains **only `app-debug.apk`**. The workflow ran on
  GitHub-hosted `ubuntu-latest` and used Android's default debug signing
  for `assembleDebug`, with **no explicit keystore source or export step**.
  The Gradle cache configuration shown in the job log caches Gradle data,
  **not** `~/.android/debug.keystore`.
- The private signing key is **not recoverable from the GitHub artifact or
  the APK certificate**. Without a separately preserved copy of that runner's
  debug keystore, in-place updates cannot use the old signing identity.
  Finding a different debug keystore does not help unless its certificate
  SHA-256 matches exactly. GitHub-hosted runner's workspace is ephemeral.

See https://github.com/lolokeksu09/claude-secure-network/actions/runs/44709849744
for the original run. The source archive and the installed APK were not
compared byte-for-byte; the signing certificate and APK byte count match.

The owner additionally checked `~/.android/debug.keystore` on the Lenovo/Termux: **file not found**. This checks one default path only, not other local build directories or computers.

## 1. Read-only audit on the Lenovo

**Correction after actual Android 17 failure:** On the Lenovo, 
`pm path --user 11` followed by `cp` to `/sdcard/Download` did **not**
create the APK. The user reported a Binder exception and then a missing input file. 
Do not assume the package path is empty or the stored APK is missing solely from that error.

Package APK installation and signing identity are shared across Android users.
Because the same package is installed in the primary profile too, prefer querying
the package without a cross-user override:

```sh
cd "$HOME"
./rish -c 'pm path dev.lolokeksu.csn.guardian'
```

If the command displays `package:/data/app/.../base.apk`, transfer only the
base APK into **Termux's own private directory**, not shared Download. 
Encoding inside the shell avoids shell storage restrictions; `pipefail`
detects failed reads:

```sh
cd "$HOME"
set -o pipefail
./rish -c 'p=$(pm path dev.lolokeksu.csn.guardian | sed -n "s/^package://p" | head -n 1); test -n "$p" && toybox base64 "$p"' |
  sed '/^Entering shell\.\.\.$/d' |
  base64 -d > "$HOME/CSN-Guardian-installed.apk" &&
python "$HOME/guardian-apk-cert.py" "$HOME/CSN-Guardian-installed.apk"
```

The Shizuku rish wrapper on this Lenovo prints `Entering shell...` to stdout.
Filter this line before decoding, otherwise `base64: invalid input` appears.

To prevent copy truncation, check output length directly:

```sh
hash=$(python "$HOME/guardian-apk-cert.py" "$HOME/CSN-Guardian-installed.apk")
printf 'Length: %s\\nSHA256: %s\\n' "${#hash}" "$hash"
```

If either command fails, keep Guardian and User 11 installed and capture only
the first command's error text. Do not remove the profile, disable Shizuku
restrictions, change SELinux or factory-reset to retrieve an APK.

This reads the installed package's **public signing certificate**, not its private
key. The copy of the installed APK is local to Termux. Never send VPN subscription
material or any keystore/private key.

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

## 2a. Termux-first key generation and local APK signing (no GitHub secrets needed)

This is the **recommended preparation path**, because the user controls the signing
identity from the first build. These scripts were added to the same
`feat/csn-guardian-dpc` branch. **Do not upload the private `.p12` keystore,
password, or its Base64 to GitHub, chat, or public cloud.**

In Termux:

```sh
pkg install openjdk-21 apksigner curl
cd "$HOME"
curl -fsSL https://raw.githubusercontent.com/lolokeksu09/claude-secure-network/feat/csn-guardian-dpc/scripts/guardian-termux-create-key.sh -o guardian-termux-create-key.sh
bash guardian-termux-create-key.sh
```

Choose a unique strong passphrase at the prompts and preserve it independently.
The script does not overwrite an existing key. It stores the **PKCS12 keystore**
in `~/.local/share/csn-guardian/signing/guardian-release.p12` and the **public**
certificate fingerprint alongside it. Key generation does not interact with
Android Device Policy Manager, VPN or work profile.

**Off-device backup is essential.** A private copy within Termux alone will be
lost if Termux is uninstalled or its data is cleared. A password-protected
PKCS12 file can be copied to the owner's trusted offline storage; keep its
password in a separate password manager, not alongside the file in public
storage. Test opening this backup in `keytool -list` on a second device before
a destructive migration. If using shared `Download` as a temporary transfer,
remove that shared copy once the offline backup is verified.

A source-verified CI workflow builds an **unsigned release candidate**, named
`csn-guardian-unsigned-release-not-installable`, in GitHub Actions for this
branch. The download is a ZIP containing `app-release-unsigned.apk`.
Do not attempt to install this unsigned APK as-is.

After obtaining and extracting it to a local location (for example Downloads):

```sh
cd "$HOME"
curl -fsSL https://raw.githubusercontent.com/lolokeksu09/claude-secure-network/feat/csn-guardian-dpc/scripts/guardian-termux-sign.sh -o guardian-termux-sign.sh
bash guardian-termux-sign.sh /storage/emulated/0/Download/app-release-unsigned.apk
```

The signer prompts for the keystore passphrase, verifies the resulting APK with
`apksigner`, and confirms its certificate matches the saved public fingerprint.
Output: `~/CSN-Guardian-newkey-signed.apk`. It is *only for a future work
profile or clean install*; it **cannot update the existing User 11 installation**.

For work-profile backup readiness, use
`scripts/guardian-workprofile-preflight.sh` to create a read-only application
inventory in the Termux private directory. It **does not export private app
data, photos, VPN configurations or passwords**. See [MIGRATION.md](MIGRATION.md)
for necessary manual exports and the approval gate before any removal.

## 3. Evidence required to close the issue

1. Record the installed Guardian certificate fingerprint from User 11.
2. Identify an available private signing key with the matching certificate. The exact GitHub run is known, but its ephemeral key has not been preserved in the available artifact.
3. Sign an APK with that key, validate it with `apksigner verify --print-certs`.
4. Install without removing User 11 and confirm version and unchanged Profile Owner.
5. Verify image-sharing fix and re-run VPN policy safety checks.
6. Store one stable signing process and backed-up key for every future build.

Step 1 **PASS** on Lenovo: installed certificate SHA-256 recorded above (64 characters). Steps 2–5 remain **NOT RUN** on the Lenovo. No original private key has been retrieved or generated in this repository.
