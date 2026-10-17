#!/usr/bin/env python3
"""Inspect and hash a CI debug APK without publishing it as a formal release."""
import hashlib
from pathlib import Path
import sys
import zipfile


def main():
    if len(sys.argv) != 2:
        print("usage: record_debug_apk.py DIRECTORY", file=sys.stderr)
        return 2
    directory = Path(sys.argv[1])
    files = sorted(directory.glob("*.apk"))
    if len(files) != 1:
        print(f"Expected exactly one debug APK in {directory}, found {len(files)}", file=sys.stderr)
        return 2
    apk = files[0]
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
        if "AndroidManifest.xml" not in names or "classes.dex" not in names:
            print("APK lacks manifest or classes.dex", file=sys.stderr)
            return 2
        if z.testzip() is not None:
            print("APK ZIP integrity failed", file=sys.stderr)
            return 2
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    print(f"DEBUG_PROBE_APK_SHA256={sha} BYTES={apk.stat().st_size} (NOT RELEASE-SIGNED)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
