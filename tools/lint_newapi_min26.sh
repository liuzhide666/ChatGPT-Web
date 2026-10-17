#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
JAVAC="$TOOLKIT_ROOT/jdk-17/bin/javac"
LINT="$TOOLKIT_ROOT/sdk/cmdline-tools/latest/bin/lint"
ANDROID_JAR="$TOOLKIT_ROOT/sdk/platforms/android-37/android.jar"
SRC="$ROOT/app/src/main/java/com/liuchenlin/chatgptweb"
STUB="$ROOT/host-test-stubs"
RES="$ROOT/app/src/main/res"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/chatgptweb-lint26.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

[[ -x "$JAVAC" ]] || { echo "Missing javac: $JAVAC" >&2; exit 2; }
[[ -x "$LINT" ]] || { echo "Missing lint: $LINT" >&2; exit 2; }
[[ -f "$ANDROID_JAR" ]] || { echo "Missing Android API jar: $ANDROID_JAR" >&2; exit 2; }

mkdir -p "$TMP/classes"
cat > "$TMP/AndroidManifest.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.liuchenlin.chatgptweb">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28" />
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  <application android:debuggable="false" />
</manifest>
XML

mapfile -t STUBS < <(find "$STUB" -type f -name '*.java' | sort)
mapfile -t PROD < <(find "$SRC" -type f -name '*.java' | sort)
"$JAVAC" -encoding UTF-8 -source 17 -target 17 -cp "$ANDROID_JAR" -d "$TMP/classes" \
  "${STUBS[@]}" "${PROD[@]}"

export JAVA_HOME="$TOOLKIT_ROOT/jdk-17"
export ANDROID_SDK_ROOT="$TOOLKIT_ROOT/sdk"
export TERM="${TERM:-dumb}"
"$LINT" --check NewApi --sdk-home "$ANDROID_SDK_ROOT" --compile-sdk-version 37 \
  --sources "$ROOT/app/src/main/java" --resources "$RES" --classpath "$TMP/classes" "$TMP"
