from importlib import util
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'tools'))
spec = util.spec_from_file_location('cross_runner', ROOT/'tools/report_cross_runner_identity.py')
mod = util.module_from_spec(spec)
spec.loader.exec_module(mod)


def item(apk='0'*64, cert='a'*64, entries=None):
    return {'apk_sha256': apk, 'apk_bytes': 12, 'signature_certificate_sha256': cert,
            'non_signature_entries': entries or {'classes.dex': 'd'*64, 'AndroidManifest.xml': 'e'*64}}


def test_different_debug_cert_same_source_payload():
    r = mod.compare(item(), item(apk='1'*64, cert='b'*64))
    assert r['outcome'] == 'SIGNER_CHANGED_PAYLOAD_IDENTICAL'
    assert r['same_non_signature_payload'] and not r['same_debug_certificate']
    assert r['device_test'] == 'NOT_RUN'


def test_different_classes_digest_is_real_content_change():
    r = mod.compare(item(), item(apk='1'*64, entries={'classes.dex': 'b'*64, 'AndroidManifest.xml':'e'*64}))
    assert r['outcome'] == 'NON_SIGNATURE_PAYLOAD_CHANGED'
    assert r['entry_diff_counts']['changed'] == 1
    assert r['changed_examples'] == ['classes.dex']


def test_same_debug_cert_same_contents_but_different_apk_bytes():
    r = mod.compare(item(), item(apk='3'*64))
    assert r['outcome'] == 'CONTAINER_OR_PACKAGING_VARIATION'


def test_cross_runner_identical():
    r = mod.compare(item(), item())
    assert r['byte_identical'] and r['outcome'] == 'CROSS_RUNNER_BYTE_IDENTICAL'


def test_signing_entry_exclusions_narrow():
    assert mod.is_signature_entry('META-INF/MANIFEST.MF')
    assert mod.is_signature_entry('META-INF/CERT.RSA')
    assert mod.is_signature_entry('META-INF/CERT.SF')
    assert not mod.is_signature_entry('META-INF/com/vendor/build.properties')
    assert not mod.is_signature_entry('lib/arm64-v8a/libruntime.so')


def test_missing_mandatory_fails():
    try:
        mod.compare({'apk_sha256': 'a'}, item())
    except ValueError as e:
        assert 'Missing required' in str(e)
    else:
        raise AssertionError('should reject invalid report')


def test_ci_matrix_is_explicitly_two_runner_jobs_and_artifact_only():
    workflow = (ROOT/'.github/workflows/apk-cross-runner.yml').read_text()
    assert 'matrix:' in workflow and 'attempt: [A, B]' in workflow
    assert 'needs: [collect-runner-identities]' in workflow
    assert 'workflow_dispatch:' in workflow
    assert "actions/download-artifact@" in workflow
    assert 'report_cross_runner_identity.py compare' in workflow
    assert 'contents: read' in workflow
    assert 'path: ${{ runner.temp }}/identity-${{ matrix.attempt }}.json' in workflow
