from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MediaDiagnostics.java"


def test_diagnostics_api_is_structured_and_has_required_tags():
    assert SRC.exists(), "MediaDiagnostics.java must exist"
    text = SRC.read_text(encoding="utf-8")
    for tag in (
        "ChatGPTWeb/Permission",
        "ChatGPTWeb/Media",
        "ChatGPTWeb/Session",
        "ChatGPTWeb/Navigation",
        "ChatGPTWeb/Recovery",
    ):
        assert tag in text
    for event in ("ANDROID_REQUEST", "ANDROID_RESULT", "MEDIA_REQUEST", "AUDIO_SOURCE", "AUDIO_SELECTED", "RECORDING"):
        assert event in text


def test_diagnostics_source_has_no_generic_sensitive_payload_logger():
    assert SRC.exists(), "MediaDiagnostics.java must exist"
    text = SRC.read_text(encoding="utf-8").lower()
    # Production diagnostics should expose semantic methods rather than generic network/payload dumps.
    forbidden = ("authorization", "cookie", "refresh_token", "access_token", "webrtc sdp", "request body", "conversation text")
    for needle in forbidden:
        assert needle not in text
