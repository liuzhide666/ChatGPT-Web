"""Behavioral regression tests for diagnosing ZIP/APK nondeterminism."""
from importlib import util
from pathlib import Path
import subprocess
import sys
import zipfile

import pytest

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'tools/compare_debug_apk_reproducibility.py'
spec = util.spec_from_file_location('apk_repro', source)
mod = util.module_from_spec(spec)
spec.loader.exec_module(mod)


def apk(path: Path, dex=b'DEX', *, timestamp=(2020, 1, 1, 0, 0, 0), extra=None):
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr('AndroidManifest.xml', b'Android manifest')
        entry = zipfile.ZipInfo('classes.dex', date_time=timestamp)
        entry.compress_type = zipfile.ZIP_DEFLATED
        if extra:
            entry.extra = extra
        z.writestr(entry, dex)
        z.writestr('lib/arm64-v8a/libmozglue.so', b'ELF')


def test_identical_byte_for_byte(tmp_path):
    a, b = tmp_path/'a.apk', tmp_path/'b.apk'
    apk(a)
    b.write_bytes(a.read_bytes())
    r = mod.compare(a, b)
    assert r['outcome'] == 'BYTE_IDENTICAL' and r['byte_identical']
    assert r['first']['abis'] == {'arm64-v8a': 1}
    assert r['production_signing'] == 'NOT_RUN'


def test_changed_dex_is_reported_as_payload(tmp_path):
    a, b = tmp_path/'a.apk', tmp_path/'b.apk'
    apk(a, dex=b'one')
    apk(b, dex=b'two')
    r = mod.compare(a, b)
    assert r['outcome'] == 'ENTRY_CONTENT_CHANGED'
    assert 'classes.dex' in r['differences']['changed_payload_examples']
    assert r['differences']['changed_payload_count'] == 1


def test_changed_timestamp_is_zip_metadata_not_dex(tmp_path):
    a, b = tmp_path/'a.apk', tmp_path/'b.apk'
    apk(a, timestamp=(2020, 1, 1, 0, 0, 0))
    apk(b, timestamp=(2021, 1, 1, 0, 0, 0))
    r = mod.compare(a, b)
    assert r['outcome'] == 'ZIP_METADATA_CHANGED'
    assert r['differences']['changed_payload_count'] == 0
    assert 'classes.dex' in r['differences']['changed_zip_metadata_examples']


def test_zip_container_only_byte_difference(tmp_path):
    a, b = tmp_path/'a.apk', tmp_path/'b.apk'
    apk(a)
    with zipfile.ZipFile(a, 'a') as z:
        z.comment = b'one'
    b.write_bytes(a.read_bytes())
    with zipfile.ZipFile(b, 'a') as z:
        z.comment = b'two'
    r = mod.compare(a, b)
    assert r['outcome'] == 'OTHER_CONTAINER_BYTES_CHANGED'
    assert r['differences']['changed_payload_count'] == 0


def test_rejects_path_escape(tmp_path):
    path = tmp_path/'bad.apk'
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('../escape.txt', b'not trusted')
        z.writestr('AndroidManifest.xml', b'X')
        z.writestr('classes.dex', b'X')
    with pytest.raises(ValueError, match='Unsafe'):
        mod.inspect(path)


def test_rejects_duplicate_entries(tmp_path):
    path = tmp_path/'bad.apk'
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('classes.dex', b'X')
        with pytest.warns(UserWarning, match='Duplicate name'):
            z.writestr('classes.dex', b'Y')
        z.writestr('AndroidManifest.xml', b'X')
    with pytest.raises(ValueError, match='duplicate'):
        mod.inspect(path)


def test_cli_nonidentical_observational_vs_strict(tmp_path):
    a, b = tmp_path/'a.apk', tmp_path/'b.apk'
    apk(a, dex=b'A')
    apk(b, dex=b'B')
    out = tmp_path/'result.json'
    command = [sys.executable, str(source), str(a), str(b), '--output', str(out)]
    observed = subprocess.run(command, capture_output=True, text=True)
    strict = subprocess.run(command+['--require-identical'], capture_output=True, text=True)
    assert observed.returncode == 0 and out.is_file()
    assert strict.returncode == 1
    assert 'ENTRY_CONTENT_CHANGED' in strict.stderr


def test_workflow_prevents_false_release_claims():
    script = (ROOT/'.github/workflows/apk-reproducibility.yml').read_text()
    assert ':app:assembleProbeDebug' in script
    assert script.count('--no-build-cache clean :app:assembleProbeDebug') == 2
    assert 'workflow_dispatch:' in script
    assert 'contents: read' in script
    assert 'upload-artifact@' in script
    assert 'apksigner" verify' in script
    assert 'path: ${{ runner.temp }}/apk-reproducibility-report.json' in script
