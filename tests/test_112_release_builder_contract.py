from pathlib import Path
from _project_paths import PROJECT_ROOT

DEX = PROJECT_ROOT / 'tools' / 'build_112_release_dex.sh'
APK = PROJECT_ROOT / 'tools' / 'build_112_rc2_candidate.sh'


def test_vc112_release_dex_builder_uses_real_android_api_and_gecko_abi_stubs():
    assert DEX.exists()
    text = DEX.read_text(encoding='utf-8')
    assert 'generate_release_r.py' in text
    assert 'host-test-stubs/org/mozilla/geckoview' in text
    assert 'host-test-stubs/android' not in text
    assert 'android-37/android.jar' in text
    assert '--min-api 26' in text
    assert 'verify_geckoview153_abi.sh' in text


def test_vc112_candidate_builder_replaces_all_rc2_runtime_payloads_and_signs_same_way():
    assert APK.exists()
    text = APK.read_text(encoding='utf-8')
    assert '--version-code 112' in text
    assert "versionCode='112'" in text
    assert 'classes3.dex' in text
    assert 'assets/chatgpt_bridge/manifest.json' in text
    assert 'assets/chatgpt_bridge/site_adapter.js' in text
    assert 'assets/chatgpt_bridge/content.js' in text
    assert 'zipalign' in text
    assert 'apksigner' in text
    assert '--v1-signing-enabled false' in text
    assert '--v2-signing-enabled true' in text
    assert '--v3-signing-enabled true' in text
    assert 'META-INF/' in text
