from pathlib import Path
from _project_paths import APP_ROOT, PROJECT_ROOT, TOOLS_ROOT
ROOT = APP_ROOT
MAIN=(ROOT/'src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text()
SESSION=(ROOT/'src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java').read_text()
BRIDGE=(ROOT/'src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java').read_text()

def test_fixed_polling_is_removed_and_deadline_supervisor_is_wired():
    assert 'PAGE_HEALTH_POLL_MS' not in MAIN
    assert 'BrowserHealthSupervisor' in MAIN
    assert 'scheduleHealthDeadline' in MAIN
    assert 'nextDelayMs' in MAIN

def test_gecko_content_signals_feed_supervisor():
    assert 'HealthObserver' in SESSION
    assert 'onProgressChange' in SESSION
    assert 'onFirstContentfulPaint' in SESSION
    assert 'onPageStart' in SESSION and 'onPageStop' in SESSION

def test_recovery_ladder_starts_with_bridge_rebind_ping():
    assert 'REBIND_BRIDGE' in MAIN
    assert 'rebindCurrentSession' in MAIN
    assert 'requestPing' in MAIN
    assert 'SOFT_RELOAD' in MAIN and 'HARD_REPLACE' in MAIN

def test_bridge_connection_state_can_signal_health_supervisor():
    assert 'ConnectionListener' in BRIDGE
    assert 'onPortConnected' in BRIDGE
    assert 'onPortDisconnected' in BRIDGE
