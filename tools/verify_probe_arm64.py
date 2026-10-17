#!/usr/bin/env python3
"""Compare current universal and opt-in ARM64-only debug probe APKs.

This is *packaging* evidence, not a compatibility or signed-release claim.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile


def inspect(path: Path) -> dict:
    abis: dict[str, int] = {}
    with zipfile.ZipFile(path) as jar:
        if jar.testzip() is not None:
            raise ValueError('APK ZIP integrity check failed')
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise ValueError('duplicate APK path')
        if 'classes.dex' not in names:
            raise ValueError('missing classes.dex')
        for name in names:
            if name.startswith('lib/') and name.endswith('.so'):
                parts = name.split('/')
                if len(parts) != 3 or not parts[1] or not parts[2]:
                    raise ValueError('invalid native-library path')
                abis[parts[1]] = abis.get(parts[1], 0) + 1
    return {'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'abis': abis}


def compare(universal: Path, arm64: Path) -> dict:
    before, after = inspect(universal), inspect(arm64)
    if not {'arm64-v8a', 'armeabi-v7a', 'x86_64'}.issubset(before['abis']):
        raise ValueError('universal control does not contain 3 expected ABIs')
    if set(after['abis']) != {'arm64-v8a'} or after['abis']['arm64-v8a'] < 1:
        raise ValueError('diagnostic APK is not exactly ARM64')
    if before['abis']['arm64-v8a'] != after['abis']['arm64-v8a']:
        raise ValueError('ARM64 native library count changed unexpectedly')
    saving = before['bytes'] - after['bytes']
    if saving <= 0:
        raise ValueError('ARM64 opt-in did not reduce APK size')
    return {'universal': before, 'arm64': after,
            'bytes_saved': saving, 'size_reduction_percent': round(saving * 100 / before['bytes'], 2),
            'device_compatibility': 'NOT_RUN', 'release_signing': 'NOT_RUN'}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('universal', type=Path)
    parser.add_argument('arm64', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        report = compare(args.universal, args.arm64)
        if args.output:
            args.output.write_text(json.dumps(report, indent=2, sort_keys=True)+'\n', encoding='utf-8')
        print('ARM64_PROBE_SIZE_REPORT '+json.dumps(report, sort_keys=True))
        return 0
    except (OSError, zipfile.BadZipFile, ValueError) as e:
        print('ARM64_PROBE_FAIL', str(e), file=sys.stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
