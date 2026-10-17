from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb"
MAIN = (SRC / "MainActivity.java").read_text()


def test_browser_diagnostics_component_exists_and_is_browser_only():
    path = SRC / "BrowserDiagnosticsDialog.java"
    assert path.exists()
    text = path.read_text()
    for token in [
        "navigationGeneration", "paintValid", "bridgeConnected", "networkAvailable",
        "recoveryState", "lastRecoveryReason", "cacheMode", "composerDirty", "composerComposing"
    ]:
        assert token in text
    for forbidden in ["scanCode", "deviceId", "transactionId", "KEYCODE_ENTER", "Shift+Enter", "promptText", "clipboard"]:
        assert forbidden not in text


def test_recovery_ui_exposes_browser_diagnostics_not_input_diagnostics():
    assert "浏览器诊断" in MAIN
    assert "BrowserDiagnosticsDialog" in MAIN
    assert "InputDiagnostics" not in MAIN
