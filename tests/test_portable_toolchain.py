"""Portable host/API37 build contract: no dependence on a historical toolkit path."""
import os
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/resolve_android_toolchain.sh"


def run_resolver(env):
    return subprocess.run(["bash", str(SCRIPT)], env=env, text=True, capture_output=True)


import pytest


@pytest.mark.parametrize("platform_dir", ["android-37", "android-37.0"])
def test_resolver_prefers_explicit_jdk_sdk(tmp_path, platform_dir):
    jdk = tmp_path / "jdk"
    sdk = tmp_path / "sdk"
    (jdk / "bin").mkdir(parents=True)
    (sdk / "platforms" / platform_dir).mkdir(parents=True)
    for executable in ["javac", "java"]:
        p = jdk / "bin" / executable
        p.write_text("#!/bin/sh\nexit 0\n")
        p.chmod(0o755)
    (sdk / "platforms" / platform_dir / "android.jar").write_bytes(b"test fixture")
    env = dict(os.environ, JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk))
    for key in ("TOOLKIT_ROOT", "JDK_ROOT", "ANDROID_JAR", "ANDROID_SDK_ROOT"):
        env.pop(key, None)
    result = run_resolver(env)
    assert result.returncode == 0, result.stderr
    assert "TOOLCHAIN_RESOLVE_OK" in result.stdout
    assert f"ANDROID_JAR={sdk}/platforms/{platform_dir}/android.jar" in result.stdout
    assert f"JAVAC={jdk}/bin/javac" in result.stdout


def test_resolver_reports_missing_android_platform(tmp_path):
    empty = tmp_path / "sdk"
    empty.mkdir()
    env = dict(os.environ, ANDROID_HOME=str(empty), ANDROID_SDK_ROOT=str(empty))
    for key in ("TOOLKIT_ROOT", "ANDROID_JAR"):
        env.pop(key, None)
    result = run_resolver(env)
    assert result.returncode != 0
    assert "android-37/android.jar" in result.stderr
    assert "sdkmanager" in result.stderr


def test_project_name_is_stable():
    assert 'rootProject.name = "ChatGPT-Web"' in (ROOT / "settings.gradle").read_text()


def test_host_runner_sources_toolchain_resolver():
    for script in ("compile_api37_contract.sh", "run_all_host_tests.sh"):
        text = (ROOT / "tools" / script).read_text()
        assert 'source "$ROOT/tools/resolve_android_toolchain.sh"' in text
        assert "/mnt/data/android-build-toolkit-linux-x86_64" not in text
