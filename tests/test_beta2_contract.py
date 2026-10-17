from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app/build.gradle"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
MAIN = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java"
VIEW = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTGeckoView.java"


def test_beta2_identity_is_source_authoritative():
    gradle = GRADLE.read_text(encoding="utf-8")
    strings = STRINGS.read_text(encoding="utf-8")

    assert "versionCode 113" in gradle
    assert 'versionName "1.1.0"' in gradle
    assert gradle.count("manifestPlaceholders = [appLabel: 'ChatGPT Web']") == 2
    assert '<string name="app_name">ChatGPT Web</string>' in strings
    assert '<string name="probe_display_name">ChatGPT Web</string>' in strings
    assert "ChatGPT 网页版" not in gradle + strings
    assert "Beta 1" not in gradle + strings



def test_download_plumbing_uses_gecko_response_and_scoped_storage_without_broad_permissions():
    controller = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java").read_text(encoding="utf-8")
    coordinator = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/DownloadCoordinator.java").read_text(encoding="utf-8")
    main = MAIN.read_text(encoding="utf-8")
    flow = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/ExternalFlowTracker.java").read_text(encoding="utf-8")
    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

    assert "onExternalResponse" in controller and "downloadHandler.handle(response)" in controller
    assert "DOWNLOAD_SAVE" in flow
    compat = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MediaStoreDownloadsCompat.java").read_text(encoding="utf-8")
    assert 'Class.forName("android.provider.MediaStore$Downloads")' in compat
    assert 'getField("EXTERNAL_CONTENT_URI")' in compat
    assert '"relative_path"' in compat
    assert 'Environment.DIRECTORY_DOWNLOADS + "/ChatGPT Web"' in compat
    assert "MediaStore.Downloads.EXTERNAL_CONTENT_URI" not in main
    assert "Intent.ACTION_CREATE_DOCUMENT" in main
    assert "DOWNLOAD_SAVE_REQUEST_CODE" in main
    assert "downloadCoordinator.onLegacySaveResult" in main
    assert "READ_EXTERNAL_STORAGE" not in manifest
    assert "WRITE_EXTERNAL_STORAGE" not in manifest
    assert "MANAGE_EXTERNAL_STORAGE" not in manifest
    assert "DownloadManager" not in coordinator + main


def test_ime_and_keyboard_semantics_are_left_to_gecko_and_web():
    main = MAIN.read_text(encoding="utf-8")
    view = VIEW.read_text(encoding="utf-8")
    assert "setSoftInputMode" not in main
    assert "onCreateInputConnection" not in view
    assert "dispatchKeyEvent" not in view
    assert not (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTInputConnection.java").exists()
    assert not (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/ImeActionPolicy.java").exists()
    assert "KEYCODE_ENTER" not in main + view
    assert "new KeyEvent" not in main + view

