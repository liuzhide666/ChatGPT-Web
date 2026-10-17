from pathlib import Path
from _project_paths import APP_ROOT, PROJECT_ROOT, TOOLS_ROOT
ROOT = APP_ROOT
S=(ROOT/'src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java').read_text()
M=(ROOT/'src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text()

def test_gecko_session_state_changes_count_as_content_liveness_only():
    assert 'onSessionStateChange' in S
    assert 'healthObserver.onSessionStateChange' in S
    assert '@Override public void onSessionStateChange(long generation, long nowMs)' in M
    assert 'browserHealthSupervisor.onContentProgress(nowMs)' in M
    assert 'restoreState(' not in S
