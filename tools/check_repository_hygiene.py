#!/usr/bin/env python3
"""Fail if forbidden secret or distribution artifacts are tracked in the repo.

A preventive filter, not a comprehensive secret scanner or release certification.
"""
from pathlib import Path
import zipfile
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
BLOCKED_ENDINGS = ('.apk', '.aab', '.hap', '.p12', '.pfx', '.jks', '.keystore', '.pem', '.key', '.7z', '.zip', '.tar.gz')
BLOCKED_NAMES = {'local.properties', '.env', 'release-signing-secret.txt'}
BLOCKED_PARTS = {'private', 'signing', 'secrets'}
ALLOWED_FLAT_ZIP = 'ChatGPT-Web-ALL-FILES-FLAT-2026-10-17.zip'

def tracked_paths():
    try:
        raw = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z'], stderr=subprocess.DEVNULL)
        return [Path(p.decode('utf-8')) for p in raw.split(b'\0') if p]
    except (OSError, subprocess.CalledProcessError):
        # During a no-repo unpack, scan only the candidate source tree (ignoring caches)
        return [p.relative_to(ROOT) for p in ROOT.rglob('*') if p.is_file() and '.git' not in p.parts]

def main():
    bad = []
    for rel in tracked_paths():
        parts = set(rel.parts)
        name = rel.name.lower()
        if rel.name == ALLOWED_FLAT_ZIP and len(rel.parts) == 1:
            try:
                with zipfile.ZipFile(ROOT / rel) as z:
                    members = z.namelist()
                    if (not members or len(members) != len(set(members))
                            or any('/' in n or '\\' in n or n in {'.', '..'} for n in members)
                            or z.testzip() is not None
                            or not {'FILE-MAP.csv', 'README-FLAT.txt', 'restore_flat.py', 'SHA256SUMS.txt'}.issubset(members)):
                        bad.append(str(rel) + ': invalid flat ZIP')
            except (OSError, zipfile.BadZipFile):
                bad.append(str(rel) + ': ZIP read failed')
            continue
        if (name in BLOCKED_NAMES or name.startswith('.env.') or name.endswith(BLOCKED_ENDINGS)
                or parts.intersection(BLOCKED_PARTS)):
            bad.append(str(rel))
    if bad:
        print('REPO_HYGIENE_FAIL forbidden tracked paths:', *bad, sep='\n')
        return 1
    print(f'REPO_HYGIENE_OK scanned={len(tracked_paths())} (file/path policy only)')
    return 0

if __name__ == '__main__':
    sys.exit(main())
