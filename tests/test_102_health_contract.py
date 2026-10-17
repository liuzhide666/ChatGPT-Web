from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb"
SUP = (JAVA / "BrowserHealthSupervisor.java").read_text()


def test_legacy_age_based_page_health_policy_is_retired():
    assert not (JAVA / "PageHealthPolicy.java").exists()
    for forbidden in ["STALE_AFTER_MS", "BRIDGE_STALE_MS", "CONTENT_STALE_MS"]:
        assert forbidden not in SUP
    for state in ["STABLE", "SUSPECT", "PROBING", "BRIDGE_RECOVERY", "DEGRADED_STABLE",
                  "SOFT_RECOVERY", "HARD_RECOVERY", "USER_RECOVERY"]:
        assert state in SUP
    for forbidden in ["clearCache", "clearData", "CACHE_MODE_NO_STORE", "WorkManager", "Service"]:
        assert forbidden not in SUP
