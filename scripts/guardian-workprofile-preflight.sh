#!/usr/bin/env bash
# Read-only readiness snapshot for the EXISTING CSN Secure Android managed profile.
# This is NOT a backup of private app data, work storage, VPN secrets or accounts.
set -euo pipefail
umask 077

profile_id="${1:-11}"
[[ "$profile_id" =~ ^[1-9][0-9]*$ ]] || {
  echo "Expected managed profile user ID > 0." >&2; exit 2;
}
rish="$HOME/rish"
[[ -f "$rish" ]] || { echo "Missing $rish (Shizuku rish bridge)"; exit 2; }

base="$HOME/.local/share/csn-guardian/preflight"
mkdir -p "$base"
chmod 700 "$base"
folder="$base/$(date '+%Y%m%dT%H%M%S')"
mkdir -m 700 "$folder"

shell_read() {
  "$rish" -c "$1" | sed '/^Entering shell\.\.\.$/d'
}
shell_read 'pm list users' > "$folder/users.txt"
shell_read 'cmd device_policy list-owners' > "$folder/owners.txt"
if ! grep -Eq "User $profile_id: admin=dev[.]lolokeksu[.]csn[.]guardian/.*ManagedProfileOwner" "$folder/owners.txt"; then
  echo "GUARD: CSN Guardian not confirmed as owner of Android User $profile_id." >&2
  echo "No changes made. Check folder $folder manually." >&2
  exit 1
fi
shell_read "pm list packages --user $profile_id -3" > "$folder/work-apps.txt"
{
  echo "Work profile: $profile_id"
  echo "Recorded: $(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "Owner: dev.lolokeksu.csn.guardian"
  echo "This folder inventories package names and policy state only."
  echo "NOT SAVED: private app data, photos, documents, VPN subscriptions, private keys."
  echo "Do not remove work profile until every important item was exported and restored."
} > "$folder/README.txt"
{
  echo "always_on_vpn_app:"
  shell_read "settings --user $profile_id get secure always_on_vpn_app" || true
  echo "always_on_vpn_lockdown:"
  shell_read "settings --user $profile_id get secure always_on_vpn_lockdown" || true
} > "$folder/vpn-policy.txt"

echo "PASS: Profile Owner confirmed. Read-only inventory in:"
echo "$folder"
echo "IMPORTANT: This is NOT a data backup. Export any important work-profile data manually."
echo "Do NOT paste the work-apps inventory or VPN credentials in public repositories."
