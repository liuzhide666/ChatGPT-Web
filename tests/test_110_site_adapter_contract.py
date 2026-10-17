import json
from _project_paths import APP_ROOT
from _vc113_keyboard_neutrality import assert_web_passive, SITE, CONTENT
MAN=json.loads((APP_ROOT/'src/main/assets/chatgpt_bridge/manifest.json').read_text())

def test_manifest_loads_read_only_site_adapter_before_transport():
    assert MAN['content_scripts'][0]['js'] == ['site_adapter.js','content.js']

def test_site_adapter_is_read_only_and_content_transport_has_no_chatgpt_selectors():
    assert_web_passive()
    assert '#prompt-textarea' in SITE
    assert '#prompt-textarea' not in CONTENT

def test_transport_retains_ping_pong_without_editor_commands():
    assert 'PING' in CONTENT and 'PONG' in CONTENT
    assert 'INSERT_LINE_BREAK' not in CONTENT and 'SUBMIT_COMPOSER' not in CONTENT
