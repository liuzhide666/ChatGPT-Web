import json
from _project_paths import APP_ROOT
GRADLE=(APP_ROOT/'build.gradle').read_text()
MANIFEST=json.loads((APP_ROOT/'src/main/assets/chatgpt_bridge/manifest.json').read_text())
CONTENT=(APP_ROOT/'src/main/assets/chatgpt_bridge/content.js').read_text()
BRIDGE=(APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java').read_text()

def test_rc3_identity_forces_builtin_extension_upgrade_after_rc2_failure():
    assert 'versionCode 113' in GRADLE
    assert 'versionName "1.1.0"' in GRADLE
    assert MANIFEST['version']=='1.1.2'

def test_ready_handshake_carries_protocol3_identity():
    assert 'const EXTENSION_VERSION = "1.1.2"' in CONTENT
    assert 'const PROTOCOL_VERSION = 3' in CONTENT
    assert 'EXPECTED_EXTENSION_VERSION = "1.1.2"' in BRIDGE
    assert 'EXPECTED_PROTOCOL_VERSION = 3' in BRIDGE

def test_protocol3_bridge_is_passive_not_semantic_authority():
    assert 'bridgeProtocolCompatible' in BRIDGE
    for token in ['requestSemanticCommand','SUBMIT_COMPOSER','INSERT_LINE_BREAK']:
        assert token not in BRIDGE
