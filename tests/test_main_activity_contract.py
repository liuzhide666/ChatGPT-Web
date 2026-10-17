from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java"
RECOVERY = ROOT / "app/src/main/res/layout/view_recovery.xml"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"


def text(path: Path) -> str:
    assert path.exists(), f"missing {path.relative_to(ROOT)}"
    return path.read_text(encoding="utf-8")


def test_activity_is_thin_gecko_shell_and_uses_controller():
    src = text(MAIN)
    for token in [
        "GeckoView", "GeckoRuntimeHolder.get", "GeckoSessionController",
        "releaseSession()", "setSession(session)",
    ]:
        assert token in src
    assert ("SystemClock::elapsedRealtime" in src or "SystemClock.elapsedRealtime()" in src)
    assert "android.webkit" not in src
    assert "userAgent" not in src


def test_android_permission_result_is_forwarded_to_coordinator():
    src = text(MAIN)
    assert "onRequestPermissionsResult" in src
    assert "androidPermissions.onPermissionsResult" in src
    assert "PackageManager.PERMISSION_GRANTED" in src


def test_file_picker_uses_saf_open_document_and_forwards_content_uris():
    src = text(MAIN)
    for token in [
        "Intent.ACTION_OPEN_DOCUMENT", "Intent.CATEGORY_OPENABLE", "Intent.EXTRA_ALLOW_MULTIPLE",
        "onActivityResult", "filePromptCoordinator.onPickerResult", "ClipData",
    ]:
        assert token in src
    for forbidden in ["READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE"]:
        assert forbidden not in src


def test_external_navigation_checks_resolver_before_start_activity():
    src = text(MAIN)
    assert "resolveActivity(getPackageManager())" in src
    assert "startActivity(intent)" in src


def test_recovery_ui_is_explicit_and_user_driven():
    src = text(MAIN)
    recovery = text(RECOVERY)
    strings = text(STRINGS)
    assert "retryAfterRecoveryUi" in src
    assert "@string/recovery_title" in recovery
    assert "@string/recovery_retry" in recovery
    assert "网页内核连续异常" in strings
    assert "重新加载" in strings


def test_shared_preferences_backend_is_wired_without_cookie_migration():
    src = text(MAIN)
    assert "getSharedPreferences" in src
    assert "SessionStateStore.Backend" not in src
    for forbidden in ["CookieManager", "cookie", "WebStorage"]:
        assert forbidden not in src


def test_launcher_reentry_explicitly_opens_fresh_chatgpt_home():
    src = text(MAIN)
    for token in [
        "onNewIntent",
        "Intent.ACTION_MAIN",
        "Intent.CATEGORY_LAUNCHER",
        "sessionController.reopenFreshHome()",
    ]:
        assert token in src
    # Ordinary lifecycle resume must remain a live-session resume, not a home reset.
    on_start = src[src.index("protected void onStart()"):src.index("protected void onStop()") ]
    assert "reopenFreshHome" not in on_start


def test_activity_destroy_closes_owned_gecko_session():
    src = text(MAIN)
    on_destroy = src[src.index("protected void onDestroy()"):src.index("public void onRequestPermissionsResult")]
    assert "sessionController.destroy()" in on_destroy
