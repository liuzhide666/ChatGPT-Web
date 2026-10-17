import json
from _project_paths import APP_ROOT

GRADLE = (APP_ROOT / 'build.gradle').read_text()
MANIFEST = json.loads((APP_ROOT / 'src/main/assets/chatgpt_bridge/manifest.json').read_text())


def test_formal_identity_uses_monotonic_android_version_code():
    assert 'versionCode 113' in GRADLE
    assert 'versionName "1.1.0"' in GRADLE
    assert MANIFEST['version'] == '1.1.2'
