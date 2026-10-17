from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"


def read(path: Path) -> str:
    assert path.exists(), f"缺少文件: {path.relative_to(ROOT)}"
    return path.read_text(encoding="utf-8")


def test_app_label_is_product_name_and_shell_recovery_text_is_chinese():
    gradle = read(ROOT / "app/build.gradle")
    strings = read(MAIN / "res/values/strings.xml")
    recovery = read(MAIN / "res/layout/view_recovery.xml")
    startup = read(MAIN / "res/layout/view_startup_recovery.xml")

    assert gradle.count("ChatGPT Web") >= 2
    assert "ChatGPT 网页版" not in gradle
    assert "ChatGPT Web Gecko Probe" not in gradle
    for expected in ["网页内核连续异常", "重新加载", "网页内核启动失败", "开始网页内核"]:
        assert expected in strings or expected in recovery or expected in startup
    assert "android:text=\"@string/" in recovery
    assert "android:text=\"@string/" in startup


def test_launcher_icon_is_restored_from_working_03x_baseline():
    manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
    app = manifest.find("application")
    assert app is not None
    assert app.get(ANDROID + "icon") == "@mipmap/ic_launcher"
    assert app.get(ANDROID + "roundIcon") == "@mipmap/ic_launcher"
    for rel in [
        "res/drawable-nodpi/ic_launcher_foreground.png",
        "res/drawable-nodpi/ic_launcher_monochrome.png",
        "res/mipmap-anydpi/ic_launcher.xml",
        "res/mipmap-anydpi-v33/ic_launcher.xml",
        "res/values/colors.xml",
    ]:
        path = MAIN / rel
        assert path.exists(), f"旧版图标资源未同步: {rel}"
        assert path.stat().st_size > 0


def test_harmony_compat_overlay_disables_app_zygote_preload_only():
    overlay = ET.parse(ROOT / "app/src/distribution/AndroidManifest.xml").getroot()
    app = overlay.find("application")
    assert app is not None
    remove = app.get(TOOLS + "remove", "")
    assert "android:zygotePreloadName" in remove
    text = read(ROOT / "app/src/distribution/AndroidManifest.xml")
    assert "GeckoChildProcessServices" not in text
    assert "tools:node=\"remove\"" not in text or "application" in text


def test_main_activity_has_startup_interruption_guard_and_chinese_recovery():
    src = read(MAIN / "java/com/liuchenlin/chatgptweb/MainActivity.java")
    store = read(MAIN / "java/com/liuchenlin/chatgptweb/StartupStateStore.java")
    for token in [
        "StartupStateStore",
        "hasInterruptedStartup()",
        "showStartupRecovery",
        "RuntimeException | LinkageError",
        "Stage.GECKO_VIEW",
        "Stage.GECKO_RUNTIME",
        "Stage.GECKO_SESSION",
    ]:
        assert token in src
    for token in ["KEY_IN_PROGRESS", "KEY_STAGE", "begin", "advance", "complete", "hasInterruptedStartup"]:
        assert token in store
