from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb"


def read(name: str) -> str:
    return (SRC / name).read_text(encoding="utf-8")


def test_content_failure_discards_failed_session_and_loads_clean_start():
    text = read("GeckoSessionController.java")
    assert "replaceWithFreshSession();" in text
    assert "old.close();" in text
    assert "restoreState(" not in text
    assert "replaceWithUri(START_URI);" in text
    assert "session.loadUri(uri);" in text
    assert 'public static final String START_URI = "https://chatgpt.com/"' in text


def test_media_trace_is_persistent_and_bounded():
    text = read("MediaCrashTraceStore.java")
    assert 'KEY_TRACE = "media_crash_trace_v1"' in text
    assert "DEFAULT_CAPACITY = 12" in text
    assert "MAX_EVENT_LENGTH = 240" in text
    assert "while (events.size() > capacity)" in text


def test_media_grant_boundary_is_persisted_without_changing_source_selection():
    diag = read("MediaDiagnostics.java")
    delegate = read("GeckoMediaPermissionDelegate.java")
    assert 'MEDIA_GRANT begin video=' in diag
    assert 'MEDIA_GRANT returned=true' in diag
    assert "diagnostics.mediaGrantBegin(selectedVideo, selectedAudio);" in delegate
    assert "safeMediaGrant(callback, selectedVideo, selectedAudio);" in delegate
    assert "callback.grant(video, audio);" in delegate
    assert "diagnostics.mediaGrantReturned();" in delegate


def test_recovery_page_can_show_recent_media_trace_without_new_resource_ids():
    main = read("MainActivity.java")
    assert "installRecoveryMediaTraceView();" in main
    assert "showRecoveryMediaTrace(diagnostics.recentTraceSummary());" in main
    assert '\\u6700\\u8fd1\\u5a92\\u4f53\\u8bca\\u65ad\\uff1a\\n' in main


def test_media_trace_uses_durable_shared_preferences_commit():
    main = read("MainActivity.java")
    assert "class MediaTracePreferencesBackend implements MediaCrashTraceStore.Backend" in main
    assert "putString(key, value).commit();" in main
    assert "remove(key).commit();" in main
