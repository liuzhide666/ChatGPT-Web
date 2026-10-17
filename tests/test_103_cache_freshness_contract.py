from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java").read_text()
CTRL = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java").read_text()
SUP = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java").read_text()
SCRIPT = (ROOT / "app/src/main/assets/chatgpt_bridge/content.js").read_text()


def test_stable_health_has_no_age_based_stale_window():
    for token in ["STALE_AFTER_MS", "BRIDGE_STALE_MS", "CONTENT_STALE_MS", "lastBridgeMs", "lastContentMs"]:
        assert token not in SUP
    assert "Long.MAX_VALUE" in SUP
    assert "composerComposing" in MAIN


def test_normal_lifecycle_never_refreshes_or_bypasses_cache():
    on_start = CTRL.split("public void onStart()", 1)[1].split("/** Final Activity teardown", 1)[0]
    on_stop = CTRL.split("public void onStop()", 1)[1].split("/** Reactivates", 1)[0]
    assert ".reload()" not in on_start and "loadUri" not in on_start and "setActive(true)" in on_start
    assert ".reload()" not in on_stop and "setActive(false)" in on_stop
    combined = "\n".join([SUP, MAIN, CTRL, SCRIPT])
    for forbidden in ["clearCache", "clearData", "CACHE_MODE_NO_STORE", "WorkManager"]:
        assert forbidden not in combined
    soft = CTRL.split("void softRecoverCurrentPage()", 1)[1].split("\n    }", 1)[0]
    hard = CTRL.split("void hardRecoverTrustedPage()", 1)[1].split("\n    }", 1)[0]
    assert "LOAD_FLAGS_BYPASS_CACHE" not in soft + hard
    force = CTRL.split("void forceRefetchCurrentPage()", 1)[1].split("\n    }", 1)[0]
    assert "LOAD_FLAGS_BYPASS_CACHE" in force


def test_long_chat_bridge_has_no_global_dom_observer_or_periodic_reload():
    assert "MutationObserver" not in SCRIPT
    assert "HEARTBEAT_MS" not in SCRIPT and "setInterval" not in SCRIPT
    assert CTRL.count("session.reload();") == 1
