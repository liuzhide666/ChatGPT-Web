from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
CTRL = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java"
MAIN = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java"

def test_long_conversation_recovery_is_bounded_and_does_not_clear_cache():
    c = CTRL.read_text(encoding="utf-8")
    m = MAIN.read_text(encoding="utf-8")
    assert "softRecoverCurrentPage" in c and ".reload()" in c
    assert "hardRecoverTrustedPage" in c and "old.close()" in c
    assert "showPageHealthRecoveryUi" in c
    for forbidden in ["clearCache", "clearData", "CACHE_MODE_NO_STORE", "CACHE_MODE_RELOAD", "WorkManager"]:
        assert forbidden not in c + m
