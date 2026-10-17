from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_GRADLE = ROOT / 'app/build.gradle'
MAIN = ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java'
POLICY = ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/SystemBarPolicy.java'
GECKO_VIEW = ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTGeckoView.java'
CONTROLLER = ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java'


def read(path: Path) -> str:
    assert path.exists(), f'missing {path.relative_to(ROOT)}'
    return path.read_text(encoding='utf-8')


def test_release_102_identity_is_frozen():
    gradle = read(APP_GRADLE)
    assert 'versionCode 113' in gradle
    assert 'versionName "1.1.0"' in gradle or "versionName '1.0.3'" in gradle
    assert "manifestPlaceholders = [appLabel: 'ChatGPT Web']" in gradle


def test_system_bars_follow_light_dark_system_mode_without_gray_default():
    policy = read(POLICY)
    main = read(MAIN)
    for token in ['isDarkMode', 'barColorArgb', 'useLightSystemBarIcons']:
        assert token in policy
    for token in [
        'applySystemBars()',
        'setStatusBarColor',
        'setNavigationBarColor',
        'SYSTEM_UI_FLAG_LIGHT_STATUS_BAR',
        'SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR',
        'onWindowFocusChanged',
    ]:
        assert token in main


def test_geckoview_is_keyboard_transparent_and_android_window_still_resizes_for_ime():
    view = read(GECKO_VIEW)
    main = read(MAIN)
    assert 'extends GeckoView' in view
    assert 'onCreateInputConnection' not in view
    assert 'dispatchKeyEvent' not in view
    assert not (ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/ImeActionPolicy.java').exists()
    assert 'new ChatGPTGeckoView(this)' in main
    manifest = read(ROOT / 'app/src/main/AndroidManifest.xml')
    assert 'android:windowSoftInputMode="adjustResize"' in manifest
    assert 'setSoftInputMode' not in main

def test_cache_friendly_lifecycle_contract_is_present():
    controller = read(CONTROLLER)
    assert not (ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/SessionStateStore.java').exists()
    assert 'restoreState(' not in controller
    assert 'external_flow=true keep_active=true' in controller
    assert 'session.setActive(false)' in controller
    assert 'session.setActive(true)' in controller
    on_start = controller.split('public void onStart()', 1)[1].split('@Override', 1)[0]
    assert 'session.reload()' not in on_start
    assert 'session.flushSessionState()' not in controller
