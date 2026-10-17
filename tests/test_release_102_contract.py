from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_release_102_identity_is_stable():
    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    assert "versionCode 113" in gradle
    assert 'versionName "1.1.0"' in gradle or "versionName '1.0.3'" in gradle
    assert "applicationId 'com.liuchenlin.chatgptweb'" in gradle or 'applicationId "com.liuchenlin.chatgptweb"' in gradle
    assert "org.mozilla.geckoview:geckoview:153.0.20260810162159" in gradle
