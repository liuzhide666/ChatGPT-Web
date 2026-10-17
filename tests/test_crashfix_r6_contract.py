from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb"
MAIN = JAVA / "MainActivity.java"
COMPAT = JAVA / "MediaStoreDownloadsCompat.java"
LAZY = JAVA / "LazyDownloadHandler.java"


def all_java_text():
    return "\n".join(p.read_text(encoding="utf-8") for p in sorted(JAVA.glob("*.java")))


def test_api29_download_class_is_not_a_symbolic_startup_dependency():
    main = MAIN.read_text(encoding="utf-8")
    assert "MediaStore.Downloads" not in main
    assert "MediaStore.MediaColumns.RELATIVE_PATH" not in main
    assert "MediaStore.MediaColumns.IS_PENDING" not in main
    assert "android.provider.MediaStore" not in main
    assert COMPAT.exists()
    compat = COMPAT.read_text(encoding="utf-8")
    assert 'Class.forName("android.provider.MediaStore$Downloads")' in compat
    assert 'getField("EXTERNAL_CONTENT_URI")' in compat
    assert '"relative_path"' in compat
    assert '"is_pending"' in compat
    assert "MediaStore.Downloads" not in compat


def test_download_subsystem_is_lazy_and_has_saf_fallback():
    main = MAIN.read_text(encoding="utf-8")
    assert LAZY.exists()
    assert "new LazyDownloadHandler" in main
    assert "new DownloadCoordinator(this" not in main.split("private DownloadCoordinator createDownloadCoordinator", 1)[0]
    coordinator = (JAVA / "DownloadCoordinator.java").read_text(encoding="utf-8")
    assert "mediaStoreDownloads()" in coordinator
    assert "beginLegacySave(metadata, response.body)" in coordinator


def test_no_java33_array_factory_dependency_remains_in_app_sources():
    text = all_java_text()
    assert ".toArray(String[]::new)" not in text


def test_crashfix_probe_identity_is_vc42():
    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    assert "versionCode 113" in gradle
    assert 'versionName "1.1.0"' in gradle
