#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/tools/resolve_android_toolchain.sh"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/chatgptweb-host-tests.XXXXXX")"
trap 'rm -rf "$OUT"' EXIT
SRC="$ROOT/app/src/main/java/com/liuchenlin/chatgptweb"
TEST="$ROOT/host-tests/com/liuchenlin/chatgptweb"
STUB="$ROOT/host-test-stubs"

[[ -x "$JDK_ROOT/bin/javac" ]] || { echo "Missing javac: $JDK_ROOT/bin/javac" >&2; exit 2; }
[[ -f "$ANDROID_JAR" ]] || { echo "Missing Android API jar: $ANDROID_JAR" >&2; exit 2; }

mkdir -p "$OUT"
mapfile -t STUBS < <(find "$STUB" -type f -name '*.java' | sort)
mapfile -t PROD < <(find "$SRC" -type f -name '*.java' ! -name 'MainActivity.java' | sort)
mapfile -t TESTS < <(find "$TEST" -type f -name '*Test.java' | sort)

"$JDK_ROOT/bin/javac" -encoding UTF-8 -source 17 -target 17 -cp "$ANDROID_JAR" -d "$OUT" \
  "${STUBS[@]}" "${PROD[@]}" "${TESTS[@]}"

passed=0
for file in "${TESTS[@]}"; do
  test_class="$(basename "$file" .java)"
  "$JDK_ROOT/bin/java" -cp "$OUT:$ANDROID_JAR" "com.liuchenlin.chatgptweb.$test_class"
  passed=$((passed + 1))
done

echo "HOST_TESTS_OK count=$passed"
