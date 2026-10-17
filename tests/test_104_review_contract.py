import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
GRADLE=(ROOT/'app/build.gradle').read_text(); MAN=json.loads((ROOT/'app/src/main/assets/chatgpt_bridge/manifest.json').read_text())
CONTENT=(ROOT/'app/src/main/assets/chatgpt_bridge/content.js').read_text(); SITE=(ROOT/'app/src/main/assets/chatgpt_bridge/site_adapter.js').read_text()
BRIDGE=(ROOT/'app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java').read_text(); SUP=(ROOT/'app/src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java').read_text(); MAIN=(ROOT/'app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text(); CTRL=(ROOT/'app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java').read_text()

def test_current_identity_and_bridge(): assert 'versionCode 113' in GRADLE and MAN['version']=='1.1.2'
def test_keyboard_fallback_handlers_are_absent(): assert 'stopImmediatePropagation' not in CONTENT and '"keydown"' not in CONTENT and '"beforeinput"' not in CONTENT
def test_bridge_requires_content_script_sender_environment(): assert 'ENV_TYPE_CONTENT_SCRIPT' in BRIDGE and 'sender.environmentType' in BRIDGE
def test_site_adapter_is_read_only_and_has_no_send_executor(): assert 'fruitjuice-send-button' not in SITE + CONTENT and '.click()' not in SITE and 'insertLineBreak' not in SITE
def test_health_is_passive_until_real_incident_and_real_state_blocks_reload():
    assert 'composerDirty' in SUP and 'composerComposing' in SUP and 'composerFocused' in SUP
    assert 'composerSendReady' not in SUP and 'destructiveBlocked' in SUP
    assert 'DEGRADED_STABLE' in SUP and 'onBridgeDisconnected' in SUP and 'onTopLevelLoadFailed' in SUP
def test_page_health_is_gated_to_current_trusted_chatgpt_page(): assert 'isCurrentPageTrusted' in CTRL and 'sessionController.isCurrentPageTrusted()' in MAIN and 'currentPageTrusted' in SUP
