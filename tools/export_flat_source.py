#!/usr/bin/env python3
"""Export all current tracked project source as a *flat directory* for one ZIP artifact.

The GitHub Actions artifact service compresses the contents of this directory;
every exported filename is at the ZIP root (no nested folders or paths).
"""
from pathlib import Path, PurePosixPath
from hashlib import sha256
import csv
import io
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
EXCLUDED = {'ChatGPT-Web-ALL-FILES-FLAT-2026-10-17.zip'}
META = {'FILE-MAP.csv', 'README-FLAT.txt', 'restore_flat.py', 'SHA256SUMS.txt'}

RESTORE = '''#!/usr/bin/env python3
"""Restore a flat ChatGPT-Web source artifact and independently verify every file."""
import csv
import hashlib
import os
import sys
from pathlib import Path, PurePosixPath

def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: python restore_flat.py FLAT_FOLDER DESTINATION")
    source = Path(sys.argv[1]).resolve()
    dest = Path(sys.argv[2]).resolve()
    if not source.is_dir() or source == dest:
        raise ValueError('input must be a flat directory and separate from output')
    dest.mkdir(parents=True, exist_ok=True)
    rows = list(csv.DictReader((source/'FILE-MAP.csv').open(newline='', encoding='utf-8')))
    for row in rows:
        path = row['original_path']
        parsed = PurePosixPath(path)
        if parsed.is_absolute() or not parsed.parts or '..' in parsed.parts or '\\\\' in path:
            raise ValueError('invalid original path: '+path)
        if row['flat_name'] != Path(row['flat_name']).name:
            raise ValueError('invalid flat entry name')
        output = dest.joinpath(*parsed.parts)
        if not output.resolve().is_relative_to(dest):
            raise ValueError('path escaped output directory')
        data = (source/row['flat_name']).read_bytes()
        if len(data) != int(row['size_bytes']) or hashlib.sha256(data).hexdigest() != row['sha256']:
            raise ValueError('hash mismatch: '+path)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(data)
        if row['git_mode'] == '100755':
            output.chmod(0o755)
    print('FLAT_RESTORE_OK files='+str(len(rows)))

if __name__ == '__main__':
    main()
'''


def get_tracked(root: Path) -> list[str]:
    raw = subprocess.check_output(['git', '-C', str(root), 'ls-files', '-z'])
    paths = sorted(x.decode('utf-8') for x in raw.split(b'\x00') if x)
    return [p for p in paths if p not in EXCLUDED]


def export(root: Path, destination: Path) -> dict:
    root, destination = root.resolve(), destination.resolve()
    if destination.exists():
        raise ValueError('export destination already exists; refusing stale output')
    if destination.is_relative_to(root):
        raise ValueError('output must not be inside repository')
    paths = get_tracked(root)
    if not paths:
        raise ValueError('no tracked project files')
    destination.mkdir(parents=True)
    rows, checksums, exported = [], {}, set()
    for i, name in enumerate(paths, 1):
        rel = PurePosixPath(name)
        if rel.is_absolute() or '..' in rel.parts or '\\' in name:
            raise ValueError('unsafe Git path')
        src = root.joinpath(*rel.parts)
        if src.is_symlink() or not src.is_file():
            raise ValueError('symlink or missing tracked source: '+name)
        flat = f'{i:04d}__{src.name}'
        if flat in META or '/' in flat or '\\' in flat or flat in exported:
            raise ValueError('flat name conflict')
        blob = src.read_bytes()
        digest = sha256(blob).hexdigest()
        (destination/flat).write_bytes(blob)
        mode = '100755' if src.stat().st_mode & 0o111 else '100644'
        rows.append([flat, 'ChatGPT-Web/'+name, len(blob), digest, mode])
        checksums[flat] = digest
        exported.add(flat)
    mapbuf = io.StringIO(newline='')
    writer = csv.writer(mapbuf)
    writer.writerow(['flat_name', 'original_path', 'size_bytes', 'sha256', 'git_mode'])
    writer.writerows(rows)
    (destination/'FILE-MAP.csv').write_text(mapbuf.getvalue(), encoding='utf-8')
    (destination/'README-FLAT.txt').write_text(
        'ChatGPT-Web project source snapshot. All ZIP members are at root; no directory paths.\n'
        'Use FILE-MAP.csv and restore_flat.py to recover the source hierarchy.\n'
        'Usage after extraction: python restore_flat.py . OUTPUT_DIRECTORY\n'
        'No signed APK, account data or keystore is included; this is NOT a device release.\n',
        encoding='utf-8')
    (destination/'restore_flat.py').write_text(RESTORE, encoding='utf-8')
    for meta in ('FILE-MAP.csv', 'README-FLAT.txt', 'restore_flat.py'):
        checksums[meta] = sha256((destination/meta).read_bytes()).hexdigest()
    (destination/'SHA256SUMS.txt').write_text(''.join(f'{digest}  {p}\n' for p,digest in sorted(checksums.items())), encoding='utf-8')
    return {'project_files': len(paths), 'root_entries': len(list(destination.iterdir()))}


def main() -> int:
    if len(sys.argv) != 2:
        print('Usage: python tools/export_flat_source.py OUTPUT_DIRECTORY', file=sys.stderr)
        return 2
    try:
        report = export(ROOT, Path(sys.argv[1]))
        print('FLAT_SOURCE_READY files={project_files} root_entries={root_entries}'.format(**report))
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as e:
        print('FLAT_SOURCE_FAIL', str(e), file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
