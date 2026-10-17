#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK_ROOT="${JDK_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64/jdk-17}"
OUT="$ROOT/.host-test-classes"
SRC="$ROOT/app/src/main/java/com/liuchenlin/chatgptweb"
TEST="$ROOT/host-tests/com/liuchenlin/chatgptweb"
STUB="$ROOT/host-test-stubs"
rm -rf "$OUT"
mkdir -p "$OUT"
"$JDK_ROOT/bin/javac" -d "$OUT" \
  "$STUB/android/content/Context.java" \
  "$STUB/org/mozilla/geckoview/GeckoRuntime.java" \
  "$SRC/TrustedOriginPolicy.java" \
  "$SRC/NavigationPolicy.java" \
  "$SRC/BackgroundReloadPolicy.java" \
  "$SRC/CrashRecoveryPolicy.java" \
  "$SRC/SessionStateStore.java" \
  "$SRC/GeckoRuntimeHolder.java" \
  "$TEST/TrustedOriginPolicyTest.java" \
  "$TEST/NavigationPolicyTest.java" \
  "$TEST/BackgroundReloadPolicyTest.java" \
  "$TEST/CrashRecoveryPolicyTest.java" \
  "$TEST/SessionStateStoreTest.java" \
  "$TEST/GeckoRuntimeHolderTest.java"
for test in \
  TrustedOriginPolicyTest \
  NavigationPolicyTest \
  BackgroundReloadPolicyTest \
  CrashRecoveryPolicyTest \
  SessionStateStoreTest \
  GeckoRuntimeHolderTest; do
  "$JDK_ROOT/bin/java" -cp "$OUT" "com.liuchenlin.chatgptweb.$test"
done
