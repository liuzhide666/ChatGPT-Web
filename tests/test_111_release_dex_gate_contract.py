from pathlib import Path
from _project_paths import PROJECT_ROOT

SCRIPT = PROJECT_ROOT / 'tools' / 'build_111_release_dex.sh'


def test_release_dex_builder_uses_real_android_jar_and_generated_release_r_only():
    assert SCRIPT.exists()
    text = SCRIPT.read_text()
    assert 'generate_release_r.py' in text
    assert 'host-test-stubs/org/mozilla/geckoview' in text
    assert 'host-test-stubs/android' not in text
    assert 'host-test-stubs/com/liuchenlin/chatgptweb/R.java' not in text
    assert 'android-37/android.jar' in text
    assert 'd8' in text
    assert '--min-api 26' in text
