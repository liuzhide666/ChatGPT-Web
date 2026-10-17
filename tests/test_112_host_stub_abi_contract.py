from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_json_stub_exposes_opt_int_used_by_protocol_v2():
    text = (ROOT / "host-test-stubs/org/json/JSONObject.java").read_text(encoding="utf-8")
    assert "int optInt(String key, int fallback)" in text


def test_gecko_stub_matches_verified_153_reload_and_loader_abi():
    text = (ROOT / "host-test-stubs/org/mozilla/geckoview/GeckoSession.java").read_text(encoding="utf-8")
    for needle in (
        "LOAD_FLAGS_NONE",
        "LOAD_FLAGS_BYPASS_CACHE",
        "void reload(int flags)",
        "static class Loader",
        "Loader uri(String uri)",
        "Loader flags(int flags)",
        "void load(Loader loader)",
    ):
        assert needle in text, needle
