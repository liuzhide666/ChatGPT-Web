from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java").read_text()
MEDIA = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MediaDiagnostics.java").read_text()
SUP = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java").read_text()


def test_browser_health_is_foreground_guarded_and_operation_deadline_driven():
    for token in ["BrowserHealthSupervisor", "onBridgeEvent", "softRecoverCurrentPage", "hardRecoverTrustedPage", "showPageHealthRecoveryUi", "scheduleHealthDeadline", "nextDelayMs"]:
        assert token in MAIN
    assert "PAGE_HEALTH_POLL_MS" not in MAIN and "BRIDGE_STALE_MS" not in SUP
    assert "isRecordingActive" in MEDIA and "externalFlows.isAnyActive()" in MAIN and "WorkManager" not in MAIN


def test_real_incident_signals_drive_health_without_idle_bookkeeping():
    for forbidden in ["lastBridgeSignalMs", "lastContentSignalMs", "lastUserInteractionMs", "markUserInteraction"]:
        assert forbidden not in MAIN
    on_start = MAIN.split("protected void onStart()",1)[1].split("protected void onStop()",1)[0]
    assert "reload" not in on_start and "loadUri" not in on_start
    for guard in ["composerFocused", "composerDirty", "composerComposing", "mediaActive", "externalFlowActive"]:
        assert guard in SUP
    assert "composerSendReady" not in SUP
