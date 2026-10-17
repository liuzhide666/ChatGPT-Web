#!/usr/bin/env python3
"""Size/ABI/ELF page-alignment report for a non-production Android APK.

APK ZIP alignment and ELF PT_LOAD alignment are independent evidence. Neither
certifies successful installation, lifecycle, authentication, or HarmonyOS use.
"""
import argparse
import collections
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import zipfile

PAGE = 16384


def elf_load_alignments(header: bytes) -> list[int] | None:
    """Read PT_LOAD p_align fields; None means the ELF header cannot be assessed."""
    if len(header) < 64 or header[:4] != b'\x7fELF' or header[4] not in (1, 2) or header[5] not in (1, 2):
        return None
    order = '<' if header[5] == 1 else '>'
    if header[4] == 2:
        phoff = struct.unpack_from(order+'Q', header, 32)[0]
        entsz, n = struct.unpack_from(order+'HH', header, 54)
        expected, align_off = 56, 48
    else:
        phoff = struct.unpack_from(order+'I', header, 28)[0]
        entsz, n = struct.unpack_from(order+'HH', header, 42)
        expected, align_off = 32, 28
    if not (expected <= entsz <= 128 and 1 <= n <= 512):
        return None
    end = phoff + entsz * n
    if end > len(header) or end > 1048576:
        return None
    out = []
    for i in range(n):
        off = phoff + i * entsz
        if struct.unpack_from(order+'I', header, off)[0] == 1:
            out.append(struct.unpack_from(order+('Q' if header[4] == 2 else 'I'), header, off+align_off)[0])
    return out or None


def group_of(name: str) -> str:
    if name.startswith('lib/'):
        bits = name.split('/')
        return f'native/{bits[1]}' if len(bits) > 2 else 'native/other'
    if name.startswith('assets/'):
        return 'assets'
    if name.startswith('res/'):
        return 'res'
    if name.endswith('.dex'):
        return 'DEX'
    if name.startswith('META-INF/'):
        return 'META-INF'
    return 'other'


def resolve_zipalign():
    binary = shutil.which('zipalign')
    if binary:
        return binary
    home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    for suffix in ('36.0.0',):
        candidate = Path(home or '') / 'build-tools' / suffix / 'zipalign'
        if home and candidate.is_file():
            return str(candidate)
    return None


def analyze(apk: Path, zipalign: str | None = None) -> dict:
    apk = Path(apk)
    sizes = collections.defaultdict(lambda: {'compressed': 0, 'uncompressed': 0, 'files': 0})
    elf = {'PASS': 0, 'FAIL': 0, 'NOT_VERIFIED': 0}
    bad_libraries = []
    seen = set()
    with zipfile.ZipFile(apk) as jar:
        infos = [z for z in jar.infolist() if not z.is_dir()]
        if jar.testzip() is not None:
            raise ValueError('APK ZIP integrity check failed')
        for info in infos:
            name = info.filename
            if name in seen or name.startswith('/') or '\\' in name or any(x in ('..', '.') for x in name.split('/')):
                raise ValueError('Unsafe or duplicate APK entry')
            seen.add(name)
            row = sizes[group_of(name)]
            row['compressed'] += info.compress_size
            row['uncompressed'] += info.file_size
            row['files'] += 1
            if name.startswith('lib/') and name.endswith('.so'):
                with jar.open(info) as stream:
                    header = stream.read(min(info.file_size, 1048576))
                aligns = elf_load_alignments(header)
                if aligns is None:
                    elf['NOT_VERIFIED'] += 1
                elif all(x >= PAGE and x % PAGE == 0 for x in aligns):
                    elf['PASS'] += 1
                else:
                    elf['FAIL'] += 1
                    bad_libraries.append({'file': name, 'min_load_align': min(aligns)})
    cmd_result = 'NOT_RUN'
    if zipalign:
        proc = subprocess.run([zipalign, '-c', '-P', '16', '-v', '4', str(apk)], capture_output=True, text=True, timeout=90, check=False)
        cmd_result = 'PASS' if proc.returncode == 0 else 'FAIL'
    return {
        'apk_bytes': apk.stat().st_size,
        'entry_count': len(seen),
        'categories': dict(sorted(sizes.items())),
        'elf_pt_load_16k': elf,
        'elf_16k_fail_examples': bad_libraries[:12],
        'zipalign_16k': cmd_result,
        'overall_16k_certified': False,  # Device and runtime tests are separate gates.
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk')
    parser.add_argument('--output', help='Optional JSON report path')
    parser.add_argument('--check-zipalign', action='store_true')
    args = parser.parse_args()
    try:
        tool = resolve_zipalign() if args.check_zipalign else None
        target = Path(args.apk)
        if target.is_dir():
            apks = sorted(target.glob('*.apk'))
            if len(apks) != 1:
                raise ValueError(f'Expected one APK in {target}, found {len(apks)}')
            target = apks[0]
        report = analyze(target, tool)
        if args.output:
            Path(args.output).write_text(json.dumps(report, indent=2, sort_keys=True)+'\n', encoding='utf-8')
        print('APK_ABI_SIZE_REPORT '+json.dumps(report, ensure_ascii=True, sort_keys=True))
        return 0
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.TimeoutExpired) as e:
        print('APK_ABI_SIZE_ERROR', type(e).__name__, str(e), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
