#!/usr/bin/env python3
"""Compare two debug APKs without mistaking a successful build for reproducibility.

An investigation tool: differences are reported as data, not suppressed. The
optional --require-identical switches to a future release-gate failure mode.
It never reads user sessions, keys, or website data.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

CHUNK = 1024 * 1024
SAMPLE_LIMIT = 20


def file_digest(path: Path) -> str:
    sha = hashlib.sha256()
    with path.open('rb') as fp:
        for chunk in iter(lambda: fp.read(CHUNK), b''):
            sha.update(chunk)
    return sha.hexdigest()


def zip_entry_digest(handle: zipfile.ZipFile, member: zipfile.ZipInfo) -> str:
    sha = hashlib.sha256()
    with handle.open(member, 'r') as fp:
        for chunk in iter(lambda: fp.read(CHUNK), b''):
            sha.update(chunk)
    return sha.hexdigest()


def zip_meta(member: zipfile.ZipInfo) -> tuple:
    """Only metadata pertinent to byte comparison; avoid leaking file contents."""
    return (member.date_time, member.compress_type, member.compress_size,
            member.file_size, member.CRC, member.flag_bits,
            member.external_attr, member.internal_attr, member.create_system,
            member.header_offset, member.extra, member.comment)


def inspect(path: Path) -> dict:
    if not path.is_file():
        raise ValueError(f'Not a readable APK file: {path.name}')
    entries = {}
    abis = {}
    with zipfile.ZipFile(path, 'r') as z:
        for item in z.infolist():
            name = item.filename
            if (not name or name in entries or name.startswith('/') or '\\' in name
                    or any(p in {'.', '..'} for p in name.split('/'))):
                raise ValueError(f'Unsafe or duplicate ZIP entry in {path.name}: {name}')
            if item.is_dir():
                continue
            entries[name] = (zip_entry_digest(z, item), zip_meta(item))
            if name.startswith('lib/') and name.endswith('.so'):
                segments = name.split('/')
                if len(segments) != 3 or not segments[1]:
                    raise ValueError(f'Invalid library path: {name}')
                abis[segments[1]] = abis.get(segments[1], 0) + 1
    if 'classes.dex' not in entries or 'AndroidManifest.xml' not in entries:
        raise ValueError(f'Expected executable APK manifest and DEX in {path.name}')
    return {'sha256': file_digest(path), 'bytes': path.stat().st_size,
            'entries': entries, 'abis': abis}


def compare(first: Path, second: Path) -> dict:
    before, after = inspect(first), inspect(second)
    left, right = before['entries'], after['entries']
    lost, added = sorted(left.keys() - right.keys()), sorted(right.keys() - left.keys())
    common = left.keys() & right.keys()
    changed_content = sorted(n for n in common if left[n][0] != right[n][0])
    changed_metadata = sorted(n for n in common if left[n][1] != right[n][1])
    same_hash = before['sha256'] == after['sha256']
    if same_hash:
        outcome = 'BYTE_IDENTICAL'
    elif lost or added:
        outcome = 'ENTRY_SET_CHANGED'
    elif changed_content:
        outcome = 'ENTRY_CONTENT_CHANGED'
    elif changed_metadata:
        outcome = 'ZIP_METADATA_CHANGED'
    else:
        # E.g. APK signing block or other ZIP-container bytes, not diagnosed here.
        outcome = 'OTHER_CONTAINER_BYTES_CHANGED'
    return {
        'outcome': outcome,
        'byte_identical': same_hash,
        'first': {'sha256': before['sha256'], 'bytes': before['bytes'],
                  'entry_count': len(left), 'abis': before['abis']},
        'second': {'sha256': after['sha256'], 'bytes': after['bytes'],
                   'entry_count': len(right), 'abis': after['abis']},
        'differences': {
            'only_in_first_count': len(lost), 'only_in_first_examples': lost[:SAMPLE_LIMIT],
            'only_in_second_count': len(added), 'only_in_second_examples': added[:SAMPLE_LIMIT],
            'changed_payload_count': len(changed_content),
            'changed_payload_examples': changed_content[:SAMPLE_LIMIT],
            'changed_zip_metadata_count': len(changed_metadata),
            'changed_zip_metadata_examples': changed_metadata[:SAMPLE_LIMIT],
        },
        'comparison_scope': 'TWO_CLEAN_BUILD_TASKS_ONE_RUNNER_SHARED_DEPENDENCY_CACHE',
        'independent_cold_cache_build': 'NOT_RUN',
        'production_signing': 'NOT_RUN',
        'device_installation': 'NOT_RUN',
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('first', type=Path)
    p.add_argument('second', type=Path)
    p.add_argument('--output', type=Path)
    p.add_argument('--require-identical', action='store_true')
    args = p.parse_args()
    try:
        data = compare(args.first, args.second)
        output = json.dumps(data, indent=2, sort_keys=True) + '\n'
        if args.output:
            args.output.write_text(output, encoding='utf-8')
        print('APK_REPRODUCIBILITY_REPORT ' + json.dumps(data, sort_keys=True))
        if not data['byte_identical']:
            print('APK_REPRODUCIBILITY_MISMATCH ' + data['outcome'], file=sys.stderr)
        return 1 if args.require_identical and not data['byte_identical'] else 0
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as e:
        print('APK_REPRODUCIBILITY_ERROR ' + type(e).__name__ + ': ' + str(e), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
