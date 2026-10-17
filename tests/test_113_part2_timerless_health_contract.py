from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SUP = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java").read_text()
MAIN = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java").read_text()
WEB = (ROOT / "app/src/main/assets/chatgpt_bridge/content.js").read_text()


def test_stable_has_no_age_based_watchdog():
    for forbidden in ["BRIDGE_STALE_MS", "CONTENT_STALE_MS", "lastBridgeMs", "lastContentMs", "lastUserMs", "isStale("]:
        assert forbidden not in SUP
    assert "setInterval" not in WEB
    assert "STABLE" in SUP and "DEGRADED_STABLE" in SUP and "PROBING" in SUP and "BRIDGE_RECOVERY" in SUP


def test_main_activity_does_not_schedule_from_generic_user_idle():
    assert "markUserInteraction" not in MAIN
    assert "lastUserInteractionMs" not in MAIN
