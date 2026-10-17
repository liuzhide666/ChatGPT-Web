from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CTRL = (ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java').read_text()
MAIN = (ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text()


def test_live_session_resume_never_uses_time_based_reload():
    on_start = CTRL.split('public void onStart()', 1)[1].split('@Override', 1)[0]
    assert 'session.reload()' not in on_start
    assert 'BackgroundReloadPolicy' not in CTRL
    assert 'BACKGROUND_RELOAD' not in CTRL
    assert 'RELOAD_AFTER_MS' not in CTRL


def test_background_deactivation_relies_on_gecko_native_state_flush():
    stop = CTRL.split('public void onStop()', 1)[1].split('public void onStart', 1)[0]
    assert 'session.setActive(false)' in stop
    assert 'session.flushSessionState()' not in stop


def test_foreground_resume_only_reactivates_live_session():
    start = CTRL.split('public void onStart', 1)[1].split('@Override', 1)[0]
    assert 'session.setActive(true)' in start
    assert '.reload()' not in start
    assert 'loadUri' not in start


def test_activity_no_longer_tracks_foreground_reload_generation():
    assert 'foregroundGeneration' not in MAIN
    assert 'sessionController.onStart();' in MAIN
