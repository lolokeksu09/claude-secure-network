# CSN Guardian — independent Android profile controller (alpha)

## Goal

Ship a small dedicated Android Device Policy Controller (DPC), **not** a copy of Shelter.
It can provision a new work profile and, when it is its actual **Profile Owner**, enforce
an Android Always-on VPN with Lockdown. The DPC does not itself tunnel packets and does
not have the INTERNET permission. It does not ingest VPN keys or subscription URLs.

## Confirmed Android constraints

- Android allows only one Profile Owner of a managed profile. The user's active work
  profile is currently controlled by Shelter (user 10). **CSN cannot take ownership of
  Shelter's existing profile by installing an APK or using Shizuku.**
- Replacing the profile owner normally requires removing the Shelter work profile
  and enrolling a new one; removal deletes work-profile applications and their data.
  **Do not remove Shelter automatically.** Backups, impact review and explicit approval
  are prerequisites to any migration.
- Android DPM policy \`setAlwaysOnVpnPackage(admin, vpn, true)\` is available to a genuine
  profile owner. The selected app must actually support Android Always-on VPN.
- A policy read-back is proof of the configured policy, **not** proof of a working tunnel
  or of no DNS/IPv6/WebRTC leaks. Android has some system-app exceptions.
- Device timezone is shared by all profiles. No setting in this app tries to impersonate
  another location; there is no standalone per-profile system timezone.

## Stage 0 — implemented in this branch

- Native Java Android application \`dev.lolokeksu.csn.guardian\`; SDK 26–35,
  no network permission, no VPN keys stored, independent from Shelter source.
- Android managed-profile provisioning activity and DeviceAdminReceiver; no attempt
  to remove a pre-existing profile.
- Clear non-owner status. VPN selector only includes services declaring
  \`android.net.VpnService\` and protected by \`BIND_VPN_SERVICE\`.
- Profile Owner only: explicit confirmation before applying
  \`DevicePolicyManager.setAlwaysOnVpnPackage(admin, packageName, true)\`;
  read-back validation of assigned package and lockdown setting.
- Explicit emergency reset of the policy for recovery, with a warning that
  traffic may become unprotected; no automatic silent reset.
- Pure policy guard tests for non-owner, absent service, invalid selection and allowed case.
- GitHub Actions debug APK build and unit tests, **not** an automatically deployed release.

## Planned gates

| Gate | Required evidence |
|---|---|
| G0 | Android build and policy unit tests (CI) |
| G1 | APK installs/opens on Lenovo, shows non-owner while Shelter owns user 10 |
| G2 | Profile migration approved with verified backups; new profile owner read-back |
| G3 | Install compatible VPN client inside new profile; confirm actual VPN service |
| G4 | Apply Always-on+Lockdown and confirm from Android DPM |
| G5 | Test egress and fail-closed on Wi-Fi/LTE, reconnect, reboot, DNS/IPv6 |
| G6 | Optional tighter enterprise controls (prevent uninstall/config changes), with tested rollback |

**Risk and rollback:** never add \`DISALLOW_CONFIG_VPN\`, \`DISALLOW_DEBUGGING_FEATURES\`
or \`setUninstallBlocked\` until a tested administrative recovery workflow exists.
An aggressive policy that locks the owner out is not considered secure.
No changes to user's existing DUORAY tunnel, timezone, Shelter data or identity.

### References

- https://developer.android.com/work/dpc/build-dpc
- https://developer.android.com/work/dpc/network-telephony
- https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- https://source.android.com/docs/core/connect/time

## Alpha 0.1.1 — device screenshot driven improvements

Observed on the Lenovo running `v0.1.0-alpha`: CSN successfully launches and reports `NOT_PROFILE_OWNER`, which is expected while Shelter owns Android user 10. It reported zero VPN services even though Android Settings shows several configured VPN apps. The **cause of this discrepancy is not yet verified on-device**. Android package visibility and VpnService metadata can affect the results of `queryIntentServices`.

Implemented improvements:
- Compact role-aware screen: hide privileged policy actions when CSN is not Profile Owner.
- Show raw VPN-service query count, eligible Always-on services and rejected candidates. A zero result is **not** described as proof that no VPN apps are installed.
- Check presence of known WireGuard package with explicit manifest package visibility. This is diagnostic only; a package's existence does **not** establish that it exposes an eligible VpnService.
- Open native Android VPN Settings and copy a sanitized diagnostic report through the clipboard. No account, VPN keys, package inventory or network permission used.
- Validate `android.net.VpnService.SUPPORTS_ALWAYS_ON` opt-out metadata before displaying eligible services.

**Remaining runtime gate:** install and test v0.1.1 on the Lenovo; compare its count with Android Settings. No migration of Shelter, VPN policy, timezone or DUORAY settings is authorized by this patch.

## Alpha 0.1.2 — measured device readback

The 0.1.1-alpha report from the Lenovo showed:
- Android API 37
- `Profile Owner: false` (as expected while Shelter owns the managed profile)
- `VPN services returned: 1`, `rejected: 0`, `eligible packages: 1`
- `WireGuard package visible: true`

**Do not infer that the single VPN service is WireGuard.** Package presence and VpnService discovery are different observations. The personal-profile query also does not enumerate the work-profile app context. The 0.1.2 diagnostic now includes `Current Android profile: personal/work` using the documented `UserManager.isManagedProfile()` API and the exact *eligible* VPN package IDs returned by the app's PackageManager query. This is intentionally not a full installed app list.

G1 (read-only UI/device) partially verified. G2-G6 remain `NOT RUN`. Android allows DPC ownership transfers only through the current owning DPC's supported transfer flow, otherwise a new managed profile may be needed. **Do not remove Shelter without backups and explicit approval.**


## Alpha 0.1.2 — verified Lenovo device result (20 Nov 2026)

The owner installed the alpha and copied its diagnostic report:

| Probe | Result |
|---|---|
| `Profile Owner` | `false` |
| Current Android profile | `personal` |
| `queryIntentServices(VpnService.SERVICE_INTERFACE)` | 1 service |
| Rejected services | 0 |
| Eligible package IDs | `com.wireguard.android` only |
| WireGuard package visibility | `true` |
| OS | Android API 37 |

**Observed fact:** the app's package manager query detects WireGuard only. DUORAY appears in the user's Android VPN settings, but no DUORAY package was returned by this query. **Neither the package name nor the cause has been independently established.** Do not infer DUORAY lacks VPN support or install privileges; package visibility and exact service registration remain to be checked. <queries> includes the VPN action and WireGuard's explicit package name.

### Next minimal read-only probe

With Shizuku running, open Termux and execute:

```sh
cd "$HOME" && ./rish -c 'sleep 10; dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -n 4'
```

Switch to **DUORAY** during the ten seconds. The output should identify the foreground DUORAY package using `ActivityRecord{... u0 PACKAGE/...}` in the personal profile or `u10` in the work profile. Do not paste any config or subscription link. If this view is empty, use the system package info screen or an alternate `dumpsys window` foreground activity probe.

After identifying the actual DUORAY package, inspect its **service registration** and Always-on opt-out metadata via Android shell before proposing explicit package visibility. Do not broaden permissions to `QUERY_ALL_PACKAGES` merely to enumerate VPN apps. No policy writes or profile migration during diagnosis.

**Security consequence:** even if the client is discoverable, it is not yet proven suitable for Android Always-on + Lockdown, and client-side `direct` routes require separate review. Prior Shelter profile and DUORAY subscription remain unchanged.

## Alpha 0.1.3 — image share work → personal (opt-in, unverified on device)

**Reported on-device:** managed profile `User 11: CSN Secure` exists; 
`cmd device_policy list-owners` reported 
`dev.lolokeksu.csn.guardian/.GuardianAdminReceiver,ManagedProfileOwner(parentUserId=0)`.
The personal Guardian copy still reports `Profile Owner: false`, which is correct for user 0.

**Observed user problem:** image/photo sharing from the managed profile to the personal profile is blocked.
**Working hypothesis:** Guardian registers no DPC-owned cross-profile share-intent filter and Android does not permit the intended forward direction by default. This is not yet reproduced with the specific source/target apps on the Lenovo.

The v0.1.3 patch adds explicit owner-only UI controls for `ACTION_SEND` and 
`ACTION_SEND_MULTIPLE`, MIME `image/*`, direction 
`DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED` (managed-origin requests 
handled by a personal-profile app). No automatic access or file copying: user selects a photo 
and a destination through Android Sharesheet. The switch defaults **off** until the owner explicitly consents.

- Disable removes **only cross-profile intent filters added by the Guardian DPC**.
  System default filters remain, per Android documentation.
- Current Guardian has no other DPC-added cross-profile filters. Any future rules must be
  added to the same centralized rebuild function before extending the feature.
- Local SharedPreferences records the *last requested policy change*; Android exposes no
  equivalent public getter for this particular filter, so it is **not** proof of current routing.
- This is a deliberate reduction of *data* isolation: any matching personal app may be selected
  by the user to receive shared image content. It does **not** change VPN routing, Lockdown,
  or grant blanket access to work-profile storage.
- Real-device test gate: install v0.1.3 over v0.1.2 in both users, open Guardian in
  managed profile, enable sharing after consent, share JPEG and PNG individually and as
  multiple images to a personal app, inspect actual transferred bytes, then disable and
  verify routing blocked again. Also confirm VPN policy unaffected. **NOT RUN**.
- Some source apps or file providers may still reject cross-profile URI grants; such
  cases require a targeted file-picker or FileProvider handoff, not broad permissions.

Reference: https://developer.android.com/reference/android/app/admin/DevicePolicyManager#addCrossProfileIntentFilter(android.content.ComponentName,android.content.IntentFilter,int)

## Upgrade blocker: ephemeral GitHub debug APK signing (20 Nov 2026)

The CI workflow relies on the default per-run Android debug keystore. This is **not
a stable signing identity** for upgrades of an installed Profile Owner app.

The APK signing-block v2 certificate fingerprints were extracted and compared:
- Earlier artifact, run `44711177603`: SHA-256
  `8fb6465a3b6451928f24c70af6c0d7bab3041d662ad78b71ebbf5e3c4f338062`.
- New v0.1.3 debug artifact, run `44714525134`: SHA-256
  `1a80a20ab2e29b933db4374bf7a2479af7497102ef719888ac1bad32497b4338`.

They differ. **Do not tell the owner to uninstall Guardian or delete User 11 to
update.** Android rejects upgrades with a different package-signing certificate.
The exact currently installed APK signing certificate has not yet been read back
from the Lenovo; the certificate mismatch between these two CI artifacts is confirmed.

Before shipping updates, establish a private, persistent, securely backed up signing key
managed through GitHub Actions secrets (never commit the keystore or passwords).
For the *existing* installed Guardian, a new permanent key alone cannot update its
package: first determine whether its original signing key still exists. If not, any
profile recreation or migration is a separate **data-destructive** operation requiring
explicit informed approval and verified backup. No reset authorized or performed.
