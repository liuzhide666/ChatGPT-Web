from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
FILES = [
    ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java",
    ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java",
    ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java",
    ROOT / "app/src/main/assets/chatgpt_bridge/content.js",
    ROOT / "app/src/main/assets/chatgpt_bridge/site_adapter.js",
]


def test_send_ready_has_no_part2_recovery_authority_or_bridge_field():
    joined = "\n".join(p.read_text() for p in FILES)
    assert "composerSendReady" not in joined
    assert "sendReady" not in joined
    assert "attachmentActive" in joined


def test_safety_blockers_are_real_user_state_only():
    sup = FILES[0].read_text()
    for token in ["composerFocused", "composerDirty", "composerComposing", "attachmentActive", "mediaActive", "externalFlowActive"]:
        assert token in sup
