from pathlib import Path
from _project_paths import APP_ROOT, PROJECT_ROOT, TOOLS_ROOT
C=(APP_ROOT/'src/main/assets/chatgpt_bridge/content.js').read_text()

def test_native_port_disconnect_schedules_content_side_reconnect():
    assert 'connectNativePort' in C
    assert 'scheduleNativeReconnect' in C
    assert 'onDisconnect.addListener' in C
    assert 'scheduleNativeReconnect()' in C

def test_visibility_resume_rearms_native_port_without_page_reload():
    assert 'document.visibilityState === "visible"' in C
    assert 'connectNativePort()' in C
    assert 'location.reload' not in C
