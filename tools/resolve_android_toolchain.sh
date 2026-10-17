#!/usr/bin/env bash
# Source from portable Java-host/API37 scripts; never sources secrets or alters release signing.
_resolve_android_toolchain() {
  if [[ -n "${JDK_ROOT:-}" ]]; then
    :
  elif [[ -n "${JAVA_HOME:-}" ]]; then
    JDK_ROOT="$JAVA_HOME"
  elif [[ -n "${TOOLKIT_ROOT:-}" ]]; then
    JDK_ROOT="$TOOLKIT_ROOT/jdk-17"
  else
    local located
    located="$(command -v javac || true)"
    if [[ -n "$located" ]]; then
      located="$(readlink -f "$located")"
      JDK_ROOT="$(dirname "$(dirname "$located")")"
    fi
  fi
  if [[ -z "${JDK_ROOT:-}" || ! -x "$JDK_ROOT/bin/javac" || ! -x "$JDK_ROOT/bin/java" ]]; then
    echo 'Toolchain: missing Java JDK with java and javac. Set JAVA_HOME (JDK 17) or JDK_ROOT.' >&2
    return 2
  fi
  JAVAC="$JDK_ROOT/bin/javac"
  JAVA="$JDK_ROOT/bin/java"

  if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    ANDROID_SDK_ROOT="${ANDROID_HOME:-${TOOLKIT_ROOT:+$TOOLKIT_ROOT/sdk}}"
  fi
  if [[ -z "${ANDROID_JAR:-}" ]]; then
    if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
      echo 'Toolchain: Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT (API 37).' >&2
      return 2
    fi
    if [[ -f "$ANDROID_SDK_ROOT/platforms/android-37.0/android.jar" ]]; then
      ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-37.0/android.jar"
    else
      ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-37/android.jar"
    fi
  fi
  if [[ ! -f "$ANDROID_JAR" ]]; then
    echo "Toolchain: missing $ANDROID_JAR; install with sdkmanager 'platforms;android-37.0' (API 37)." >&2
    return 2
  fi
  export JDK_ROOT JAVAC JAVA ANDROID_SDK_ROOT ANDROID_JAR
}
_resolve_android_toolchain || { return 2 2>/dev/null || exit 2; }
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  printf 'JAVAC=%s\nJAVA=%s\nANDROID_JAR=%s\nTOOLCHAIN_RESOLVE_OK\n' "$JAVAC" "$JAVA" "$ANDROID_JAR"
fi
