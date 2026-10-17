#!/usr/bin/env python3
"""Review the committed Gradle SHA-256 verification metadata (not a provenance claim).

Bootstrapped checksums mean TOFU at first download; independent release authenticity
and Gradle plugin vulnerability review remain separate and necessary.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

NS = '{https://schema.gradle.org/dependency-verification}'
HEX = re.compile(r'[a-fA-F0-9]{64}\Z')
ROOT = Path(__file__).resolve().parent.parent


def inspect(path: Path) -> tuple[int, list[str]]:
    errors: list[str] = []
    if not path.is_file():
        return 0, ['verification metadata is missing']
    if path.stat().st_size > 2_000_000:
        return 0, ['verification metadata is unexpectedly large']
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        return 0, [f'xml syntax: {exc}']
    root = tree.getroot()
    if root.tag != NS + 'verification-metadata':
        errors.append('unexpected Gradle verification XML namespace')
    config = root.find(NS + 'configuration')
    if config is None or config.findtext(NS + 'verify-metadata') != 'true':
        errors.append('Gradle metadata checks must be enabled')
    # No allowlist exemptions: reviewers must audit changed artifact digests.
    if root.find('.//' + NS + 'trusted-artifacts') is not None:
        errors.append('trusted-artifacts bypass is not permitted')
    if root.find('.//' + NS + 'ignored-keys') is not None:
        errors.append('ignored-keys bypass is not permitted')
    comps = root.findall('.//' + NS + 'component')
    if not comps:
        errors.append('no dependency components')
    identities: set[tuple[str, str, str]] = set()
    total = 0
    for comp in comps:
        ident = tuple(comp.get(x, '') for x in ('group', 'name', 'version'))
        if not all(ident) or any('+' in x or 'SNAPSHOT' in x.upper() for x in ident):
            errors.append(f'non-static or empty coordinates: {ident}')
        if ident in identities:
            errors.append(f'duplicate dependency component: {ident}')
        identities.add(ident)
        for artifact in comp.findall(NS + 'artifact'):
            name = artifact.get('name', '')
            hashes = artifact.findall(NS + 'sha256')
            if not name or len(hashes) != 1 or not HEX.fullmatch(hashes[0].get('value', '')):
                errors.append(f'missing or invalid SHA-256: {ident}/{name}')
            else:
                total += 1
    if total < 10:
        errors.append(f'implausibly sparse checksum coverage: {total}')
    return total, errors


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('path', nargs='?', type=Path, default=ROOT / 'gradle/verification-metadata.xml')
    args = ap.parse_args(argv)
    n, problems = inspect(args.path)
    if problems:
        for problem in problems:
            print('GRADLE_DEPENDENCIES_METADATA_FAIL', problem, file=sys.stderr)
        return 1
    print(f'GRADLE_DEPENDENCIES_METADATA_OK sha256_artifacts={n}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
