from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
MAIN = APP / "src" / "main"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def read(path: Path) -> str:
    assert path.exists(), f"missing required file: {path.relative_to(ROOT)}"
    return path.read_text(encoding="utf-8")


def source_text() -> str:
    assert MAIN.exists(), "app/src/main must exist"
    chunks = []
    for path in MAIN.rglob("*"):
        if path.is_file() and path.suffix.lower() in {".java", ".kt", ".xml", ".js", ".html"}:
            chunks.append(path.read_text(encoding="utf-8", errors="replace"))
    return "\n".join(chunks)


def manifest_root():
    path = MAIN / "AndroidManifest.xml"
    assert path.exists(), "AndroidManifest.xml must exist"
    return ET.parse(path).getroot()


def test_build_identity_and_pinned_versions():
    app_gradle = read(APP / "build.gradle")
    settings = read(ROOT / "settings.gradle")
    root_gradle = read(ROOT / "build.gradle")

    assert "com.android.application" in root_gradle
    assert "9.1.1" in root_gradle
    assert "maven.mozilla.org/maven2" in settings
    assert re.search(r"compileSdk\s+37\b", app_gradle)
    assert re.search(r"minSdk\s+26\b", app_gradle)
    assert re.search(r"targetSdk\s+28\b", app_gradle)
    assert re.search(r"versionCode\s+113\b", app_gradle)
    assert 'versionName "1.1.0"' in app_gradle
    assert "JavaVersion.VERSION_17" in app_gradle
    assert "org.mozilla.geckoview:geckoview:153.0.20260810162159" in app_gradle


def test_probe_and_formal_share_one_source_tree():
    app_gradle = read(APP / "build.gradle")
    assert "com.liuchenlin.chatgptweb" in app_gradle
    assert "applicationIdSuffix" in app_gradle
    assert ".geckoprobe" in app_gradle
    strings = read(MAIN / "res" / "values" / "strings.xml")
    assert "ChatGPT Web" in strings
    assert "ChatGPT 网页版" not in strings
    assert "Gecko Beta 1" not in strings
    assert "ChatGPT Web Gecko Probe" not in strings
    assert not (APP / "src" / "probe" / "java").exists(), "Probe must not fork production Java source"
    assert not (APP / "src" / "formal" / "java").exists(), "Formal variant must not fork production Java source"


def test_universal_apk_has_no_abi_narrowing():
    text = read(APP / "build.gradle")
    assert "abiFilters" not in text
    assert "splits {" not in text or "abi" not in text


def test_manifest_has_exact_security_and_permission_baseline():
    root = manifest_root()
    uses_permissions = {
        node.get(ANDROID_NS + "name")
        for node in root.findall("uses-permission")
    }
    expected = {
        "android.permission.INTERNET",
        "android.permission.RECORD_AUDIO",
        "android.permission.MODIFY_AUDIO_SETTINGS",
        "android.permission.CAMERA",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.WAKE_LOCK",
        "android.permission.HIGH_SAMPLING_RATE_SENSORS",
    }
    assert uses_permissions == expected

    forbidden = {
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
    }
    assert not (uses_permissions & forbidden)

    application = root.find("application")
    assert application is not None
    assert application.get(ANDROID_NS + "allowBackup") == "false"
    assert application.get(ANDROID_NS + "usesCleartextTraffic") == "false"
    assert application.get(ANDROID_NS + "hardwareAccelerated") == "true"
    assert application.get(ANDROID_NS + "resizeableActivity") == "true"

    activity = application.find("activity")
    assert activity is not None
    assert activity.get(ANDROID_NS + "windowSoftInputMode") == "adjustResize"


def test_optional_hardware_features_are_not_required():
    root = manifest_root()
    required_by_name = {
        node.get(ANDROID_NS + "name"): node.get(ANDROID_NS + "required")
        for node in root.findall("uses-feature")
    }
    for feature in (
        "android.hardware.microphone",
        "android.hardware.camera",
        "android.hardware.location",
    ):
        assert required_by_name.get(feature) == "false", f"{feature} must be required=false"


def test_retired_webview_dictation_and_permission_shim_are_absent():
    text = source_text()
    forbidden = [
        "android.webkit.WebView",
        "android.webkit.WebChromeClient",
        "android.webkit.PermissionRequest",
        "SpeechRecognizer",
        "RecognizerIntent",
        "WebPermissionCompatScript",
        "NativeDictationScript",
        "navigator.permissions",
        "chatgptweb://native-dictation",
    ]
    for needle in forbidden:
        assert needle not in text, f"retired 0.3.x technology found: {needle}"


def test_real_gecko_user_agent_is_not_overridden():
    text = source_text()
    forbidden = [
        "userAgentOverride",
        "setUserAgent",
        "Chrome/",
        "CriOS/",
    ]
    for needle in forbidden:
        assert needle not in text, f"UA spoofing is forbidden in 0.4.0: {needle}"


def test_distribution_overlay_preserves_library_required_permissions():
    text = read(APP / "src" / "distribution" / "AndroidManifest.xml")
    for permission in (
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.WAKE_LOCK",
        "android.permission.HIGH_SAMPLING_RATE_SENSORS",
        "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    ):
        assert permission not in text, f"distribution overlay must not remove library permission: {permission}"
