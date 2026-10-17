#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
APK="${1:?usage: verify_geckoview153_abi.sh BASELINE_APK}"
DEXDUMP="$TOOLKIT_ROOT/sdk/build-tools/36.0.0/dexdump"
[[ -f "$APK" ]] || { echo "Missing APK: $APK" >&2; exit 2; }
[[ -x "$DEXDUMP" ]] || { echo "Missing dexdump: $DEXDUMP" >&2; exit 2; }
TMP="$(mktemp -d "${TMPDIR:-/tmp}/chatgptweb-gv153-abi.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
unzip -p "$APK" classes5.dex > "$TMP/classes5.dex"
"$DEXDUMP" -d "$TMP/classes5.dex" > "$TMP/classes5.dump"
require() {
  local needle="$1"
  grep -Fq "$needle" "$TMP/classes5.dump" || {
    echo "Missing GeckoView 153 ABI symbol: $needle" >&2
    exit 3
  }
}
require 'onPortMessage:(Ljava/lang/Object;Lorg/mozilla/geckoview/WebExtension$Port;)V'
require 'postMessage:(Lorg/json/JSONObject;)V'
require 'onPaintStatusReset:(Lorg/mozilla/geckoview/GeckoSession;)V'
require 'onSlowScript:(Lorg/mozilla/geckoview/GeckoSession;Ljava/lang/String;)Lorg/mozilla/geckoview/GeckoResult;'
require 'SlowScriptResponse;.CONTINUE'
echo 'GECKOVIEW153_FORMAL_ABI_OK'
