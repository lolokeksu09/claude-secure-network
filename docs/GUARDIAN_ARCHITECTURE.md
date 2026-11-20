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
