from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
CTRL = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java").read_text()
MAIN = (ROOT / "app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java").read_text()


def test_navigation_generation_is_browser_lifecycle_only():
    assert "navigationGeneration" in CTRL
    assert "currentNavigationGeneration" in MAIN
    assert "onPageStart(String url, long generation, long nowMs)" in CTRL
    assert "onFirstContentfulPaint(long generation, long nowMs)" in CTRL
    assert "if (generation != currentNavigationGeneration) return;" in MAIN
    assert "inputGeneration" not in CTRL + MAIN
