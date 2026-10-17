import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java"
MANIFEST = ROOT / "app/src/main/assets/chatgpt_bridge/manifest.json"
SCRIPT = ROOT / "app/src/main/assets/chatgpt_bridge/content.js"


def test_bridge_files_and_scope_are_minimal():
    assert BRIDGE.exists()
    assert MANIFEST.exists()
    assert SCRIPT.exists()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assert manifest["version"] == "1.1.2"
    scripts = manifest["content_scripts"]
    assert scripts[0]["matches"] == ["https://chatgpt.com/*"]
    assert "<all_urls>" not in MANIFEST.read_text(encoding="utf-8")
    permissions = set(manifest.get("permissions", []))
    assert {"geckoViewAddons", "nativeMessaging", "nativeMessagingFromContent"} <= permissions


def test_bridge_transmits_state_not_chat_content():
    bridge = BRIDGE.read_text(encoding="utf-8")
    script = SCRIPT.read_text(encoding="utf-8")
    for token in ["READY", "COMPOSER_STATE", "PONG"]:
        assert token in bridge
        assert token in script
    for removed in ["INPUT_ROUTE", "EDITOR_EFFECT", "COMMAND_ACK", "SUBMIT_COMPOSER", "INSERT_LINE_BREAK"]:
        assert removed not in bridge + script
    assert "HEARTBEAT" not in script
    assert "DOCUMENT_ACTIVITY" not in bridge + script
    assert "QUIET" not in bridge + script
    assert "MutationObserver" not in script
    for forbidden in ["innerText", "innerHTML", "textContent:", "localStorage", "document.cookie", "fetch(", "XMLHttpRequest", "WebSocket"]:
        assert forbidden not in script
    assert "sender.session" in bridge
    assert "sender.isTopLevel()" in bridge
    assert "chatgpt.com" in bridge


def test_bridge_failure_does_not_leave_unhandled_native_message_rejection():
    script = SCRIPT.read_text(encoding="utf-8")
    assert "sendNativeMessage" in script
    assert ".catch(() => {})" in script


def test_bundled_bridge_is_updated_without_reinstalling_same_version_every_start():
    bridge = BRIDGE.read_text(encoding="utf-8")
    assert "ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)" in bridge
    assert "installBuiltIn(EXTENSION_LOCATION)" not in bridge
