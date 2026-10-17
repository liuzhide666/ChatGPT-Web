import json
from _project_paths import APP_ROOT

ROOT = APP_ROOT.parent
GRADLE = (APP_ROOT / 'build.gradle').read_text()
MANIFEST_PATH = APP_ROOT / 'src/main/assets/chatgpt_bridge/manifest.json'
MANIFEST = json.loads(MANIFEST_PATH.read_text())
CONTENT = (APP_ROOT / 'src/main/assets/chatgpt_bridge/content.js').read_text()
BRIDGE = (APP_ROOT / 'src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java').read_text()
FAILURE_DOC = ROOT / 'docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-RC2-vc112-TARGET-FAILURE-2026-08-27.md'


def test_vc113_identity_is_monotonic_after_vc112_target_failure():
    assert 'versionCode 113' in GRADLE
    assert 'versionName "1.1.0"' in GRADLE
    assert MANIFEST['version'] == '1.1.2'


def test_protocol3_ready_identity_is_explicit_on_both_sides():
    assert 'const EXTENSION_VERSION = "1.1.2"' in CONTENT
    assert 'const PROTOCOL_VERSION = 3' in CONTENT
    assert 'extensionVersion: EXTENSION_VERSION' in CONTENT
    assert 'protocolVersion: PROTOCOL_VERSION' in CONTENT
    assert 'EXPECTED_EXTENSION_VERSION = "1.1.2"' in BRIDGE
    assert 'EXPECTED_PROTOCOL_VERSION = 3' in BRIDGE


def test_vc112_target_failure_evidence_is_frozen_before_vc113_changes():
    assert FAILURE_DOC.exists()
    text = FAILURE_DOC.read_text()
    assert 'versionCode=112' in text
    assert 'TARGET_DEVICE_GATE=FAIL' in text
    assert 'SHIFT_ENTER_TARGET_DEVICE_FIXED=NO' in text
    assert 'vc113' in text
    assert 'Canonical remains 1.0.3 Formal' in text
