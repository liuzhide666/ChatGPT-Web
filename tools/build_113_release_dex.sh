#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
BASELINE_APK="${1:?usage: build_113_release_dex.sh BASELINE_APK OUTPUT_DIR}"
OUTPUT_DIR="${2:?usage: build_113_release_dex.sh BASELINE_APK OUTPUT_DIR}"
JDK="$TOOLKIT_ROOT/jdk-17"
SDK="$TOOLKIT_ROOT/sdk"
ANDROID_JAR="$SDK/platforms/android-37/android.jar"
AAPT="$SDK/build-tools/36.0.0/aapt"
D8="$SDK/build-tools/36.0.0/d8"
DEXDUMP="$SDK/build-tools/36.0.0/dexdump"

[[ -f "$BASELINE_APK" ]] || { echo "Missing baseline APK: $BASELINE_APK" >&2; exit 2; }
[[ -x "$JDK/bin/javac" && -f "$ANDROID_JAR" && -x "$AAPT" && -x "$D8" ]] || {
  echo "Android/JDK toolchain incomplete under $TOOLKIT_ROOT" >&2; exit 2;
}

TOOLKIT_ROOT="$TOOLKIT_ROOT" bash "$ROOT/tools/verify_geckoview153_abi.sh" "$BASELINE_APK"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/classes" "$OUTPUT_DIR/dex" "$OUTPUT_DIR/release-r/com/liuchenlin/chatgptweb"

python3 "$ROOT/tools/generate_release_r.py" \
  --aapt "$AAPT" \
  --apk "$BASELINE_APK" \
  --source-root "$ROOT/app/src/main/java/com/liuchenlin/chatgptweb" \
  --out "$OUTPUT_DIR/release-r/com/liuchenlin/chatgptweb/R.java" \
  > "$OUTPUT_DIR/release-r-ids.txt"

mapfile -t PROD < <(find "$ROOT/app/src/main/java/com/liuchenlin/chatgptweb" -type f -name '*.java' | sort)
mapfile -t GECKO_STUBS < <(find "$ROOT/host-test-stubs/org/mozilla/geckoview" -type f -name '*.java' | sort)

"$JDK/bin/javac" -encoding UTF-8 -source 17 -target 17 -cp "$ANDROID_JAR" \
  -d "$OUTPUT_DIR/classes" \
  "${GECKO_STUBS[@]}" "${PROD[@]}" \
  "$OUTPUT_DIR/release-r/com/liuchenlin/chatgptweb/R.java"

(
  cd "$OUTPUT_DIR/classes"
  "$JDK/bin/jar" cf "$OUTPUT_DIR/app-classes.jar" com/liuchenlin/chatgptweb
)
"$D8" --min-api 26 --output "$OUTPUT_DIR/dex" "$OUTPUT_DIR/app-classes.jar"
cp "$OUTPUT_DIR/dex/classes.dex" "$OUTPUT_DIR/classes3.dex"

"$DEXDUMP" -d "$OUTPUT_DIR/classes3.dex" > "$OUTPUT_DIR/dexdump.txt"
python3 - "$OUTPUT_DIR/dexdump.txt" <<'PY'
import re, sys
text=open(sys.argv[1], encoding='utf-8', errors='replace').read()
classes=re.findall(r"Class descriptor\s+: '([^']+)'", text)
foreign=[c for c in classes if not c.startswith('Lcom/liuchenlin/chatgptweb/')]
if foreign:
    raise SystemExit('foreign DEX classes: ' + ', '.join(foreign[:10]))
if 'Landroid/provider/MediaStore$Downloads;' in text:
    raise SystemExit('direct MediaStore.Downloads descriptor present')
print(f'APP_DEX_CLASS_COUNT={len(classes)}')
print('APP_DEX_FOREIGN_CLASS_COUNT=0')
PY
sha256sum "$OUTPUT_DIR/classes3.dex"
echo "CHATGPT_WEB_1_1_0_VC113_RELEASE_DEX_OK"
