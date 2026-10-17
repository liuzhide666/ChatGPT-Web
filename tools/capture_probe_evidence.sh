#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PROBE_PACKAGE:-com.liuchenlin.chatgptweb.geckoprobe}"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
ADB="${ADB:-$TOOLKIT_ROOT/sdk/platform-tools/adb}"

usage() {
  echo "Usage: $0 <serial> clear | $0 <serial> capture [output-dir]" >&2
  exit 2
}

[[ $# -ge 2 ]] || usage
SERIAL="$1"
ACTION="$2"
OUT="${3:-probe-evidence}"
[[ -x "$ADB" ]] || { echo "adb not found: $ADB" >&2; exit 2; }

adb_cmd() { "$ADB" -s "$SERIAL" "$@"; }
[[ "$(adb_cmd get-state 2>/dev/null || true)" == "device" ]] || {
  echo "target is not an authorized online adb device" >&2
  exit 3
}

case "$ACTION" in
  clear)
    adb_cmd logcat -c
    echo "PROBE_LOGCAT_CLEARED"
    ;;
  capture)
    adb_cmd shell pm path "$PACKAGE" >/dev/null 2>&1 || {
      echo "Probe package not installed: $PACKAGE" >&2
      exit 4
    }
    mkdir -p "$OUT"
    {
      echo "package=$PACKAGE"
      printf 'android_sdk='; adb_cmd shell getprop ro.build.version.sdk | tr -d '\r'
      printf 'abi_list='; adb_cmd shell getprop ro.product.cpu.abilist | tr -d '\r'
      printf 'launcher='; adb_cmd shell cmd package resolve-activity --brief "$PACKAGE" | tr -d '\r'
      adb_cmd shell dumpsys package "$PACKAGE" \
        | grep -E 'versionCode=|versionName=|targetSdk=|minSdk=' \
        | sed -E 's/^[[:space:]]+//'
      adb_cmd shell pm path "$PACKAGE" | tr -d '\r'
    } > "$OUT/device-summary.txt"

    tmp="$(mktemp)"
    trap 'rm -f "$tmp"' EXIT
    adb_cmd logcat -d -v threadtime \
      | grep -E 'ChatGPTWeb/(Permission|Media|Session|Navigation|Recovery)' \
      > "$tmp" || true

    if grep -Eai '(authorization|bearer[[:space:]]|access[_ -]?token|refresh[_ -]?token|cookie|session[_ -]?token|conversation[ _-]?(text|body)|webrtc[ _-]?sdp|request[ _-]?body)' "$tmp" >/dev/null; then
      rm -f "$tmp"
      trap - EXIT
      echo "Refusing to save evidence: sensitive-pattern guard matched a project log line" >&2
      exit 5
    fi
    mv "$tmp" "$OUT/logcat-media.txt"
    trap - EXIT
    echo "PROBE_EVIDENCE_CAPTURED $OUT"
    ;;
  *) usage ;;
esac
