# CSN Guardian — protected migration, User 11 → new stable-key profile

**Status: PREPARATION ONLY.** No work-profile removal or application reinstall has
been authorized by this plan. Current Android 17 / API 37:
- Personal profile: user 0.
- Existing managed profile: `User 11 — CSN Secure`.
- DPC: `dev.lolokeksu.csn.guardian/.GuardianAdminReceiver`, `ManagedProfileOwner`.
- Installed signing cert SHA-256:
  `b471b54672ebed98ac3d4de547d1a4c4661c7c20b78a10df0d0337a25e5c55a6`.
- Original key used by temporary GitHub Actions runner 18 is unavailable.
- `v0.1.3-alpha` built with another ephemeral key will not update User 11.

## Phase A — safe preparation (allowed now)

1. Run `scripts/guardian-workprofile-preflight.sh` in Termux, using Shizuku
   `rish`. It confirms owner and creates a **read-only inventory** of app
   package names / VPN policy. **Not a data backup.** Files stay in Termux.
2. Open `User 11` work apps and export important user-created material using
   each app's supported export. Include personal documents, work-profile
   photos, VPN client configuration and authentication, saved browser state
   if it matters, and any private app documents. Keep secrets in a private,
   encrypted backup; never share them in screenshots, public GitHub, or chat.
   An app's internal data is **not** included in a PackageManager inventory;
   without root an arbitrary complete backup of all work app data cannot
   be promised. If data cannot be exported, **do not remove the profile**.
3. Make backups readable on independent storage and verify you can restore
   them in a disposable app or by opening the exported files. Do not rely on
   the same phone's Termux private storage alone.
4. Generate a **NEW** signing key privately on the Lenovo using
   `scripts/guardian-termux-create-key.sh`. Keep its passphrase in a password
   manager; back up the passphrase-protected `.p12` in an offline location.
   Its fingerprint will differ from User 11's certificate; that is expected.
5. CI in this branch produces an `app-release-unsigned.apk` artifact. Download
   it separately, sign it with `scripts/guardian-termux-sign.sh` using the
   new local key and check `apksigner verify` + stored SHA-256 fingerprint.
   An **unsigned** APK is not installable. A new-key APK **cannot update User 11**.

## Phase B — before ANY destructive migration

All conditions are required:
- [ ] User reviewed all work apps and confirmed no irreplaceable data.
- [ ] Essential photos/docs/VPN configuration exported and restored/test-read.
- [ ] New signing keystore exists, has an offline backup and its password is
      independently recoverable.
- [ ] New signed APK produced, signature checked and safely stored off-phone.
- [ ] Provisioning on Android 17 and new work-profile ownership flow reviewed.
- [ ] User explicitly approves removal of the **current** `User 11`,
      having been informed that its data/apps will be deleted.

No script here performs `pm remove-user`, `pm uninstall`, factory reset,
policy owner removal, auto profile reprovisioning, or modifies VPN lockdown.

## Phase C — gated migration (NOT AUTHORIZED / NOT RUN)

Only after all Phase B checks and a **new explicit approval**:
1. Stop activity in work profile and take final backup.
2. Remove existing managed profile through a supported Android Settings flow.
   This **deletes all data of User 11**, including its app instances.
3. Install the newly signed Guardian as a personal app; use Android's managed
   profile provisioning wizard to create a NEW managed profile, whose user ID
   must be discovered rather than assumed to be 11.
4. Verify `cmd device_policy list-owners` and read back
   `Profile Owner: true` from the **work** Guardian instance.
5. Restore only needed work apps/configurations. Bring up verified VPN first,
   then authorize Always-on + Lockdown explicitly; measure IPv4/IPv6/DNS
   and test fail-closed before use. Retest explicit work → personal photo sharing.
6. Ensure future builds use the same backed-up signing key, not CI debug keys.
   If APK fails update even after migration, STOP and investigate fingerprints
   without deleting another profile.

Android work profiles separate app data from personal profiles. See
https://source.android.com/docs/devices/admin/managed-profiles and
https://developer.android.com/work/dpc/build-dpc .
