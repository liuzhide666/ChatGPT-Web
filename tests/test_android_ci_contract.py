from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_android_ci_uses_unprivileged_pinned_tools_and_actual_compile():
    text = (ROOT / ".github/workflows/android-build.yml").read_text()
    for required in (
        "contents: read", "runs-on: ubuntu-24.04", "java-version: '17'",
        "sdkmanager --install", "platforms;android-37", "build-tools;36.0.0", "gradle-version: '9.3.1'",
        "bash tools/run_all_host_tests.sh", "bash tools/compile_api37_contract.sh",
        ":app:assembleProbeDebug", "tools/record_debug_apk.py",
    ):
        assert required in text
    assert "pull_request_target:" not in text
    assert "secrets." not in text
    assert "upload-artifact" not in text


def test_apk_recorder_never_sets_release_label():
    text = (ROOT / "tools/record_debug_apk.py").read_text()
    assert "NOT RELEASE-SIGNED" in text
    assert "sha256" in text
    assert "AndroidManifest.xml" in text
