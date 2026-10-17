from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java").read_text()
CTRL = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java").read_text()


def test_network_restore_does_not_rebind_ping_or_reload_by_itself():
    start = MAIN.index("private void onNetworkAvailabilityChanged")
    body = MAIN[start: MAIN.index("private void onRecoveryBlockerChanged", start)]
    assert "rebindCurrentSession" not in body
    assert "requestPing" not in body
    assert "sessionController.softRecover" not in body
    assert "sessionController.hardRecover" not in body
    assert "sessionController.forceRefetch" not in body


def test_bypass_cache_is_user_only_and_automatic_recovery_default_cache():
    assert "softRecoverCurrentPage" in CTRL and "session.reload();" in CTRL
    hard = CTRL[CTRL.index("public void hardRecoverTrustedPage"): CTRL.index("public void forceRefetchCurrentPage")]
    assert "LOAD_FLAGS_BYPASS_CACHE" not in hard
    force = CTRL[CTRL.index("public void forceRefetchCurrentPage"): CTRL.index("public void showPageHealthRecoveryUi")]
    assert "LOAD_FLAGS_BYPASS_CACHE" in force


def test_recents_and_foreground_reuse_live_session():
    start = CTRL.index("public void onStart()")
    body = CTRL[start: CTRL.index("public void destroy()", start)]
    assert "setActive(true)" in body
    assert "loadUri" not in body and "reload" not in body and "replace" not in body.lower()
