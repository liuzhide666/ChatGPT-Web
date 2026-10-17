#!/usr/bin/env python3
"""Generate the minimal production R.java from the resource IDs in an already-linked baseline APK.

This is intentionally separate from host-test-stubs/R.java.  The baseline APK's resources.arsc
is the authority for IDs when rebuilding only the application DEX offline.
"""
from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path

R_REF = re.compile(r"\bR\.(layout|id|string)\.([A-Za-z_][A-Za-z0-9_]*)\b")
SPEC = re.compile(r"spec resource 0x([0-9a-fA-F]+)\s+[^\s:]+:(layout|id|string)/([A-Za-z_][A-Za-z0-9_]*):")


def collect_r_refs(source_root: Path) -> set[tuple[str, str]]:
    refs: set[tuple[str, str]] = set()
    for path in sorted(source_root.rglob('*.java')):
        refs.update(R_REF.findall(path.read_text(encoding='utf-8')))
    return refs


def extract_resource_ids(dump_text: str, refs: set[tuple[str, str]]) -> dict[tuple[str, str], int]:
    found: dict[tuple[str, str], int] = {}
    for raw_id, rtype, name in SPEC.findall(dump_text):
        key = (rtype, name)
        if key in refs:
            value = int(raw_id, 16)
            prior = found.get(key)
            if prior is not None and prior != value:
                raise ValueError(f'conflicting resource id for {rtype}/{name}: 0x{prior:08x} vs 0x{value:08x}')
            found[key] = value
    missing = sorted(refs - set(found))
    if missing:
        raise ValueError('resource IDs missing from baseline APK: ' + ', '.join(f'{t}/{n}' for t, n in missing))
    return found


def render_r_java(package_name: str, ids: dict[tuple[str, str], int]) -> str:
    groups: dict[str, list[tuple[str, int]]] = {}
    for (rtype, name), value in sorted(ids.items()):
        groups.setdefault(rtype, []).append((name, value))
    lines = [f'package {package_name};', '', 'public final class R {', '    private R() {}']
    for rtype in sorted(groups):
        lines.append(f'    public static final class {rtype} {{')
        lines.append(f'        private {rtype}() {{}}')
        for name, value in groups[rtype]:
            lines.append(f'        public static final int {name} = 0x{value:08x};')
        lines.append('    }')
    lines.append('}')
    lines.append('')
    return '\n'.join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apk', required=True)
    parser.add_argument('--source-root', required=True)
    parser.add_argument('--out', required=True)
    parser.add_argument('--package', default='com.liuchenlin.chatgptweb')
    args = parser.parse_args()

    refs = collect_r_refs(Path(args.source_root))
    if not refs:
        raise SystemExit('no R references found in production sources')
    proc = subprocess.run([args.aapt, 'dump', 'resources', args.apk], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    ids = extract_resource_ids(proc.stdout, refs)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render_r_java(args.package, ids), encoding='utf-8')
    for (rtype, name), value in sorted(ids.items()):
        print(f'{rtype}/{name}=0x{value:08x}')


if __name__ == '__main__':
    main()
