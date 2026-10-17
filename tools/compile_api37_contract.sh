#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
JAVAC="$TOOLKIT_ROOT/jdk-17/bin/javac"
ANDROID_JAR="${ANDROID_JAR:-$TOOLKIT_ROOT/sdk/platforms/android-37/android.jar}"
SRC="$ROOT/app/src/main/java/com/liuchenlin/chatgptweb"
STUB="$ROOT/host-test-stubs"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/chatgptweb-api37.XXXXXX")"
trap 'rm -rf "$OUT"' EXIT

[[ -x "$JAVAC" ]] || { echo "Missing javac: $JAVAC" >&2; exit 2; }
[[ -f "$ANDROID_JAR" ]] || { echo "Missing Android API jar: $ANDROID_JAR" >&2; exit 2; }

mapfile -t STUBS < <(find "$STUB" -type f -name '*.java' | sort)
mapfile -t PROD < <(find "$SRC" -type f -name '*.java' | sort)

"$JAVAC" -encoding UTF-8 -source 17 -target 17 -cp "$ANDROID_JAR" -d "$OUT" \
  "${STUBS[@]}" "${PROD[@]}"

echo "ANDROID_API37_CONTRACT_COMPILE_OK"
