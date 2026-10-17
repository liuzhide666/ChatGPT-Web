from pathlib import Path
from _project_paths import APP_ROOT, PROJECT_ROOT, TOOLS_ROOT

ROOT = APP_ROOT
PATCHER = TOOLS_ROOT / 'patch_release_manifest.py'
MANIFEST = (ROOT / 'src/main/AndroidManifest.xml').read_text()


def test_release_manifest_patcher_is_persistent_and_forces_non_debuggable():
    assert PATCHER.exists()
    text = PATCHER.read_text()
    assert 'versionCode' in text
    assert 'versionName' in text
    assert 'debuggable' in text
    assert 'TYPE_INT_BOOLEAN' in text
    assert 'debuggable attribute not patched' in text


def test_source_manifest_declares_release_safety_defaults():
    assert 'android:debuggable="false"' in MANIFEST
    assert 'android:allowBackup="false"' in MANIFEST
    assert 'android:usesCleartextTraffic="false"' in MANIFEST
