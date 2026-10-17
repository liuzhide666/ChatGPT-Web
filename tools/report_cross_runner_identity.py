#!/usr/bin/env python3
"""Report compare-only APK identity across two isolated GitHub runner jobs.

Signer fingerprints are public certificate metadata; no APK, signing keys or
accounts are uploaded. Ordinary signatures can vary across fresh debug hosts.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

from compare_debug_apk_reproducibility import inspect


SIGNER = re.compile(r'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]{64,})')


def is_signature_entry(name: str) -> bool:
    """Skip only top-level JAR v1 signer files, not unrelated META-INF assets."""
    upper = name.upper()
    if not upper.startswith('META-INF/') or upper.count('/') != 1:
        return False
    basename = upper.partition('/')[2]
    return basename == 'MANIFEST.MF' or basename.endswith(('.SF', '.RSA', '.DSA', '.EC'))


def build_identity(apk: Path, apksigner: str) -> dict:
    item = inspect(apk)
    result = subprocess.run([apksigner, 'verify', '--print-certs', str(apk)],
                            capture_output=True, text=True, timeout=90, check=True)
    found = SIGNER.search(result.stdout)
    if not found:
        raise ValueError('Signer certificate fingerprint not reported')
    fingerprint = found.group(1).replace(':', '').lower()
    if len(fingerprint) != 64:
        raise ValueError('Unexpected signer SHA-256 format')
    entries = {n: dig for n, (dig, _) in item['entries'].items() if not is_signature_entry(n)}
    running = hashlib.sha256()
    for name, digest in sorted(entries.items()):
        running.update(name.encode('utf-8') + b'\0' + bytes.fromhex(digest))
    return {'apk_sha256': item['sha256'], 'apk_bytes': item['bytes'],
            'signature_certificate_sha256': fingerprint,
            'non_signature_payload_digest': running.hexdigest(),
            'non_signature_entries': entries,
            'non_signature_entry_count': len(entries),
            'signing_identity': 'DEBUG_ONLY_NOT_RELEASE',
            'device_test': 'NOT_RUN'}


def compare(first: dict, second: dict) -> dict:
    mandatory = {'apk_sha256', 'signature_certificate_sha256', 'non_signature_entries'}
    if not mandatory.issubset(first) or not mandatory.issubset(second):
        raise ValueError('Missing required build-identity field')
    x, y = first['non_signature_entries'], second['non_signature_entries']
    left, right = set(x), set(y)
    changed = sorted(k for k in left & right if x[k] != y[k])
    one, two = sorted(left - right), sorted(right - left)
    same_payload = not (one or two or changed)
    same_signer = first['signature_certificate_sha256'] == second['signature_certificate_sha256']
    same_apk = first['apk_sha256'] == second['apk_sha256']
    if same_apk:
        outcome = 'CROSS_RUNNER_BYTE_IDENTICAL'
    elif same_payload and not same_signer:
        outcome = 'SIGNER_CHANGED_PAYLOAD_IDENTICAL'
    elif same_payload and same_signer:
        outcome = 'CONTAINER_OR_PACKAGING_VARIATION'
    else:
        outcome = 'NON_SIGNATURE_PAYLOAD_CHANGED'
    return {'outcome': outcome, 'byte_identical': same_apk,
            'same_debug_certificate': same_signer, 'same_non_signature_payload': same_payload,
            'first': {k: first[k] for k in ('apk_sha256', 'apk_bytes', 'signature_certificate_sha256',
                                           'non_signature_payload_digest') if k in first},
            'second': {k: second[k] for k in ('apk_sha256', 'apk_bytes', 'signature_certificate_sha256',
                                             'non_signature_payload_digest') if k in second},
            'entry_diff_counts': {'first_only': len(one), 'second_only': len(two),
                                  'changed': len(changed)},
            'changed_examples': changed[:20], 'first_only_examples': one[:20],
            'second_only_examples': two[:20],
            'comparison_scope': 'TWO_SEPARATE_GITHUB_HOSTED_RUNNER_JOBS_DEPENDENCY_CACHE_MAY_BE_SHARED',
            'fresh_unauthenticated_dependencies': 'NOT_RUN',
            'production_signing': 'NOT_RUN', 'device_test': 'NOT_RUN'}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='command', required=True)
    build = sub.add_parser('build')
    build.add_argument('apk', type=Path)
    build.add_argument('--apksigner', required=True)
    build.add_argument('--output', type=Path, required=True)
    check = sub.add_parser('compare')
    check.add_argument('first', type=Path)
    check.add_argument('second', type=Path)
    check.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    try:
        if args.command == 'build':
            result = build_identity(args.apk, args.apksigner)
            print('APK_CROSS_RUNNER_IDENTITY ' + json.dumps({
                k: v for k, v in result.items() if k != 'non_signature_entries'}, sort_keys=True))
        else:
            result = compare(json.loads(args.first.read_text(encoding='utf-8')),
                             json.loads(args.second.read_text(encoding='utf-8')))
            print('APK_CROSS_RUNNER_COMPARISON ' + json.dumps(result, sort_keys=True))
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True)+'\n', encoding='utf-8')
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as e:
        print('APK_CROSS_RUNNER_ERROR '+type(e).__name__+': '+str(e), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
