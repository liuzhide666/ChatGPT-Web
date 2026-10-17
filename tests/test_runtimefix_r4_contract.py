from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb"

def read(name: str) -> str:
    return (SRC / name).read_text(encoding="utf-8")

def test_media_attempt_sentinel_is_persistent_and_user_recoverable():
    store = read("MediaCrashTraceStore.java")
    main = read("MainActivity.java")
    assert "beginAttempt()" in store
    assert "hasInterruptedAttempt()" in store
    assert "acknowledgeInterruptedAttempt()" in store
    assert "hasInterruptedAttempt()" in main
    assert "showInterruptedMediaRecovery" in main

def test_dead_session_external_callbacks_are_abandoned_before_recovery():
    controller = read("GeckoSessionController.java")
    permissions = read("AndroidPermissionCoordinator.java")
    prompt = read("FilePromptCoordinator.java")
    main = read("MainActivity.java")
    assert "replacementCleanup.run();" in controller
    assert "abandonPending()" in permissions
    assert "abandonPending()" in prompt
    assert "androidPermissions.abandonPending()" in main
    assert "filePromptCoordinator.abandonPending()" in main

def test_platform_and_gecko_callback_exceptions_do_not_escape_media_boundary():
    permissions = read("AndroidPermissionCoordinator.java")
    media = read("GeckoMediaPermissionDelegate.java")
    diagnostics = read("MediaDiagnostics.java")
    assert "host.requestPermissions" in permissions and "catch (RuntimeException" in permissions
    assert "safeMediaGrant" in media
    assert "safePermissionGrant" in media
    assert "mediaCallbackException" in diagnostics

def test_empty_media_request_is_rejected_instead_of_granting_null_null():
    media = read("GeckoMediaPermissionDelegate.java")
    assert "EMPTY_MEDIA_REQUEST" in media

def test_interrupted_media_recovery_restores_normal_startup_button_handler_before_gecko_init():
    main = read("MainActivity.java")
    assert "showInterruptedMediaRecovery(backend)" in main
    assert "startupRetryButton.setOnClickListener(v -> retryStartup(backend));" in main
    media_method = main.split("private void showInterruptedMediaRecovery", 1)[1].split("private void showStartupRecovery", 1)[0]
    assert "retryStartup(backend)" in media_method
    assert media_method.index("retryStartup(backend)") < media_method.index("initializeGecko();")
