#!/data/data/com.termux/files/usr/bin/sh
# Claude Secure Network — read-only Android compatibility audit.
# Run from Termux with an authorized Shizuku/rish shell (preferred) or ADB.
set -eu

case "${1:-}" in
  --help|-h)
    printf '%s\n' 'Usage: sh device_audit.sh [--help]' 'Reads Android profile/VPN policy hints; does not change device settings.'
    exit 0
    ;;
  '') ;;
  *) printf '%s\n' 'Unsupported argument. Use --help.' >&2; exit 2 ;;
esac

if [ -n "${CSN_SHELL:-}" ]; then
  case "$CSN_SHELL" in
    rish) run_shell() { rish -c "$1"; } ;;
    adb) run_shell() { adb shell "$1"; } ;;
    *) printf '%s\n' 'CSN_SHELL accepts only rish or adb.' >&2; exit 2 ;;
  esac
elif command -v rish >/dev/null 2>&1; then
  run_shell() { rish -c "$1"; }
elif [ -x "$HOME/rish" ]; then
  run_shell() { "$HOME/rish" -c "$1"; }
elif command -v adb >/dev/null 2>&1; then
  run_shell() { adb shell "$1"; }
else
  printf '%s\n' 'No rish/adb found. Start Shizuku and set up rish, or connect authorized ADB.' >&2
  exit 3
fi

# Prevent Termux-only commands from being mistaken for privileged Android shell checks.
uid="$(run_shell 'id -u' 2>/dev/null | tr -d '\r\n ' || true)"
if [ "$uid" != '2000' ]; then
  printf '%s\n' "Authorized Android shell not available (uid=$uid). Check Shizuku/rish or ADB authorization." >&2
  exit 4
fi

stamp="$(date +%Y%m%d-%H%M%S)"
output_dir="${CSN_OUTPUT_DIR:-/storage/emulated/0/Download}"
if [ ! -d "$output_dir" ] || [ ! -w "$output_dir" ]; then
  output_dir="$HOME"
  printf '%s\n' 'Download unavailable: saving report to Termux HOME.' >&2
fi
umask 077
report="$output_dir/claude-secure-network-audit-$stamp.txt"

# Keep errors visible as "unavailable", not proof of absence of a feature/policy.
probe() {
  key="$1"
  command_text="$2"
  printf '%s: ' "$key"
  result="$(run_shell "$command_text" 2>&1)" && status=0 || status=$?
  if [ "$status" -eq 0 ]; then
    result="$(printf '%s' "$result" | tr -d '\r' | head -n 4)"
    [ -n "$result" ] || result='(empty)'
    printf '%s\n' "$result"
  else
    printf 'UNAVAILABLE (exit=%s)\n' "$status"
  fi
}

{
  printf '%s\n' '# Claude Secure Network — read-only device audit'
  printf 'Captured (device local): %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
  printf '%s\n' 'No serial number, accounts, public IP, VPN keys, or tokens requested.'
  printf '\n%s\n' '## Device'
  probe 'android_release' 'getprop ro.build.version.release'
  probe 'android_sdk' 'getprop ro.build.version.sdk'
  probe 'brand' 'getprop ro.product.brand'
  probe 'model' 'getprop ro.product.model'
  probe 'device' 'getprop ro.product.device'
  probe 'managed_users_feature' 'pm list features | grep -F android.software.managed_users || true'

  printf '\n%s\n' '## User/profile identifiers (no names)'
  raw_users="$(run_shell 'pm list users' 2>&1)" && users_status=0 || users_status=$?
  if [ "$users_status" -ne 0 ]; then
    printf 'user_list: UNAVAILABLE (exit=%s)\n' "$users_status"
    user_ids='0'
  else
    user_ids="$(printf '%s\n' "$raw_users" | sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p' | sort -n -u)"
    [ -n "$user_ids" ] || user_ids='0'
    printf 'user_ids: %s\n' "$(printf '%s\n' "$user_ids" | tr '\n' ' ')"
  fi
  printf '%s\n' 'Note: multiple user IDs do not prove a managed work profile exists.'

  printf '\n%s\n' '## Device policy owners (package IDs only)'
  # Errors are not equivalent to "no owner"; no raw dumpsys is collected.
  probe 'device_policy_owners' 'cmd device_policy list-owners'

  for user_id in $user_ids; do
    case "$user_id" in *[!0-9]*|'') continue ;; esac
    printf '\n## User %s: VPN policy settings (hints, not proof of enforcement)\n' "$user_id"
    probe 'always_on_vpn_app' "settings --user $user_id get secure always_on_vpn_app"
    probe 'always_on_vpn_lockdown' "settings --user $user_id get secure always_on_vpn_lockdown"
    printf '%s\n' '## Relevant installed app IDs'
    probe 'wireguard' "pm list packages --user $user_id com.wireguard.android"
    probe 'shelter' "pm list packages --user $user_id net.typeblog.shelter"
    probe 'claude' "pm list packages --user $user_id com.anthropic.claude"
  done

  printf '\n%s\n' '## Limitations'
  printf '%s\n' '- READ ONLY: no configuration applied or modified.'
  printf '%s\n' '- A listed setting is not evidence of working Lockdown or an active tunnel.'
  printf '%s\n' '- Profile type and UI policy availability require follow-up inspection.'
  printf '%s\n' '- IPv4, IPv6, DNS, WebRTC, fail-closed, and reboot tests NOT RUN.'
  printf '%s\n' '- Do not upload unreviewed device reports to this PUBLIC GitHub repository.'
} > "$report"

printf 'Audit report: %s\n' "$report"
printf '%s\n' 'Open it locally. Share the report in chat after reviewing its contents.'
