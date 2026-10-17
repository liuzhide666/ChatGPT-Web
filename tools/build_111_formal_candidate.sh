#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
BASELINE_APK="${1:?usage: build_111_formal_candidate.sh BASELINE_APK CLASSES3_DEX OUTPUT_APK PKCS12 PASSWORD_FILE}"
CLASSES3_DEX="${2:?missing classes3.dex}"
OUTPUT_APK="${3:?missing output apk}"
PKCS12="${4:?missing PKCS12 signer}"
PASSWORD_FILE="${5:?missing signer password file}"
SDK="$TOOLKIT_ROOT/sdk"
BT="$SDK/build-tools/36.0.0"

for p in "$BASELINE_APK" "$CLASSES3_DEX" "$PKCS12" "$PASSWORD_FILE"; do
  [[ -f "$p" ]] || { echo "Missing required file: $p" >&2; exit 2; }
done
for p in "$BT/zipalign" "$BT/apksigner" "$BT/aapt"; do
  [[ -x "$p" ]] || { echo "Missing tool: $p" >&2; exit 2; }
done

TMP="$(mktemp -d "${TMPDIR:-/tmp}/chatgptweb-vc111.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

unzip -p "$BASELINE_APK" AndroidManifest.xml > "$TMP/AndroidManifest-rc.bin"
python3 "$ROOT/tools/patch_release_manifest.py" \
  --manifest-in "$TMP/AndroidManifest-rc.bin" \
  --manifest-out "$TMP/AndroidManifest-vc111.bin" \
  --version-code 111 \
  --old-version-name 1.1.0 \
  --version-name 1.1.0

python3 - "$BASELINE_APK" "$TMP/unsigned.apk" "$TMP/AndroidManifest-vc111.bin" "$CLASSES3_DEX" "$ROOT/app/src/main/assets/chatgpt_bridge/content.js" <<'PY'
from pathlib import Path
import sys, zipfile
src, out, manifest, dex, content = map(Path, sys.argv[1:])
repl={
    'AndroidManifest.xml': manifest.read_bytes(),
    'classes3.dex': dex.read_bytes(),
    'assets/chatgpt_bridge/content.js': content.read_bytes(),
}
with zipfile.ZipFile(src, 'r') as zin, zipfile.ZipFile(out, 'w', allowZip64=True) as zout:
    for zi in zin.infolist():
        if zi.filename.startswith('META-INF/'):
            continue
        data = repl.get(zi.filename, zin.read(zi.filename))
        nzi = zipfile.ZipInfo(zi.filename, date_time=zi.date_time)
        nzi.compress_type = zi.compress_type
        nzi.comment = zi.comment
        nzi.extra = zi.extra
        nzi.internal_attr = zi.internal_attr
        nzi.external_attr = zi.external_attr
        nzi.create_system = zi.create_system
        nzi.flag_bits = zi.flag_bits
        zout.writestr(nzi, data)
PY

"$BT/zipalign" -P 16 -f 4 "$TMP/unsigned.apk" "$TMP/aligned.apk"
"$BT/apksigner" sign \
  --ks "$PKCS12" --ks-type PKCS12 --ks-pass "file:$PASSWORD_FILE" \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$OUTPUT_APK" "$TMP/aligned.apk"

"$BT/zipalign" -c -P 16 -v 4 "$OUTPUT_APK" >/dev/null
VERIFY="$($BT/apksigner verify --verbose --print-certs "$OUTPUT_APK")"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$VERIFY"
grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' <<<"$VERIFY"
BADGING="$($BT/aapt dump badging "$OUTPUT_APK" | sed -n '1p')"
grep -q "versionCode='111'" <<<"$BADGING"
grep -q "versionName='1.1.0'" <<<"$BADGING"
sha256sum "$OUTPUT_APK"
echo "CHATGPT_WEB_1_1_0_VC111_CANDIDATE_BUILD_OK"
