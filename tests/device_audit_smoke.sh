#!/usr/bin/env bash
set -euo pipefail
project="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/bin" "$fixture/out" "$fixture/home"
cat > "$fixture/bin/rish" <<'STUB'
#!/usr/bin/env sh
if [ "$1" != '-c' ]; then exit 2; fi
case "$2" in
  'id -u') echo "${TEST_UID:-2000}" ;;
  'pm list users') printf '%s\n' 'Users:' ' UserInfo{0:Owner:13} running' ' UserInfo{10:Secret Name:30} running' ;;
  'cmd device_policy list-owners') echo 'No owners' ;;
  'pm list features | grep -F android.software.managed_users || true') echo 'feature:android.software.managed_users' ;;
  'getprop ro.build.version.release') echo '17' ;;
  'getprop ro.build.version.sdk') echo '37' ;;
  'getprop ro.product.brand') echo 'motorola' ;;
  'getprop ro.product.model') echo 'motorola edge 70 max' ;;
  'getprop ro.product.device') echo 'avr' ;;
  'settings --user 0 get secure always_on_vpn_app') echo 'null' ;;
  'settings --user 10 get secure always_on_vpn_app') echo 'com.wireguard.android' ;;
  'settings --user 0 get secure always_on_vpn_lockdown') echo '0' ;;
  'settings --user 10 get secure always_on_vpn_lockdown') echo '1' ;;
  pm\ list\ packages\ *) echo 'package:com.wireguard.android' ;;
  *) echo "Unexpected command: $2" >&2; exit 21 ;;
esac
STUB
chmod +x "$fixture/bin/rish"
PATH="$fixture/bin:$PATH" HOME="$fixture/home" CSN_OUTPUT_DIR="$fixture/out" \
  sh "$project/scripts/device_audit.sh" > "$fixture/stdout"
report="$(find "$fixture/out" -type f -name 'claude-secure-network-audit-*.txt' | head -n 1)"
test -n "$report"
grep -q 'user_ids: 0 10 ' "$report"
grep -q 'always_on_vpn_lockdown: 1' "$report"
grep -q 'android_release: 17' "$report"
if grep -q 'Secret Name' "$report"; then echo 'Privacy regression: profile name leaked' >&2; exit 1; fi
if PATH="$fixture/bin:$PATH" HOME="$fixture/home" CSN_OUTPUT_DIR="$fixture/out" TEST_UID=10234 \
  sh "$project/scripts/device_audit.sh" > "$fixture/deny.stdout" 2> "$fixture/deny.stderr"; then
  echo 'Expected rejection without Android shell UID 2000' >&2; exit 1
fi
grep -q 'uid=10234' "$fixture/deny.stderr"
if [ "$(find "$fixture/out" -type f -name 'claude-secure-network-audit-*.txt' | wc -l)" -ne 1 ]; then
  echo 'Unauthorized run created report' >&2; exit 1
fi
printf '%s\n' 'PASS: shell syntax, mock Android policy report, name privacy, no-shell refusal'
