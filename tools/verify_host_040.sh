#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
JDK_ROOT="${JDK_ROOT:-$TOOLKIT_ROOT/jdk-17}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLKIT_ROOT/sdk/platforms/android-37/android.jar}"
ADB="${ADB:-$TOOLKIT_ROOT/sdk/platform-tools/adb}"
OUT="$ROOT/.android-contract-classes"

cd "$ROOT"
python3 -m pytest tests -q
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/run_all_host_tests.sh

rm -rf "$OUT"; mkdir -p "$OUT"
mapfile -t GECKO_STUBS < <(find host-test-stubs/org/mozilla/geckoview -name '*.java' | sort)
mapfile -t PROD < <(find app/src/main/java -name '*.java' | sort)
"$JDK_ROOT/bin/javac" -source 17 -target 17 -cp "$ANDROID_JAR" -d "$OUT" \
  host-test-stubs/com/liuchenlin/chatgptweb/R.java \
  "${GECKO_STUBS[@]}" "${PROD[@]}"
echo "ANDROID_CONTRACT_COMPILE_OK"

if rg -n 'android\.webkit\.WebView|WebChromeClient|android\.webkit\.PermissionRequest|SpeechRecognizer|RecognizerIntent|WebPermissionCompatScript|NativeDictationScript|navigator\.permissions' app/src/main; then
  echo "FORBIDDEN_WEBVIEW_OR_DICTATION_SYMBOL_FOUND" >&2
  exit 1
fi
echo "FORBIDDEN_SYMBOL_SCAN_OK"

if rg -ni '(Authorization:|Bearer [A-Za-z0-9._-]{12,}|sk-[A-Za-z0-9]{12,}|refresh[_-]?token[[:space:]]*=|access[_-]?token[[:space:]]*=)' app; then
  echo "SECRET_PATTERN_SCAN_FAILED" >&2
  exit 1
fi
echo "SECRET_PATTERN_SCAN_OK"

if [[ -x "$ADB" ]]; then
  count="$($ADB devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')"
  echo "ADB_ONLINE_DEVICE_COUNT=$count"
else
  echo "ADB_ONLINE_DEVICE_COUNT=unavailable"
fi

echo "HOST_VERIFICATION_OK"
