from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


def test_android_ci_always_logs_canonical_nonsignature_payload():
    ci=(ROOT/'.github/workflows/android-build.yml').read_text()
    assert 'Record nonsecret probe APK SHA-256' in ci
    assert 'Report stable non-signature payload digest and debug signer' in ci
    assert 'report_cross_runner_identity.py build' in ci
    assert '--apksigner "$ANDROID_HOME/build-tools/36.0.0/apksigner"' in ci
    assert '--output "$RUNNER_TEMP/probe-build-identity.json"' in ci
    assert 'upload-artifact' not in ci  # debug identities and 528MB APK remain runner-local


def test_unchanged_release_identity_and_pinned_gecko():
    app=(ROOT/'app/build.gradle').read_text()
    for term in ("applicationId 'com.liuchenlin.chatgptweb'", 'versionCode 113',
                 'versionName "1.1.0"', 'geckoview:153.0.20260810162159'):
        assert term in app


def test_source_ci_covers_ledger_contract():
    source=(ROOT/'.github/workflows/source-checks.yml').read_text()
    assert 'python3 -m pytest -q' in source
    assert 'contents: read' in source
