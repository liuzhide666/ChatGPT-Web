"""Non-network dependency bootstrap and Wrapper supply-chain contracts."""
from pathlib import Path
import importlib.util
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def get_verifier():
    spec = importlib.util.spec_from_file_location('verify_gradle_wrapper', ROOT / 'tools/verify_gradle_wrapper.py')
    assert spec and spec.loader
    obj = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj


def test_wrapper_is_real_and_verified():
    assert get_verifier().verify(ROOT) == []


def test_wrapper_disallows_wrong_checksum(tmp_path):
    mod = get_verifier()
    target = tmp_path / 'repo'
    path = target / 'gradle/wrapper/gradle-wrapper.jar'
    path.parent.mkdir(parents=True)
    path.write_bytes(b'invalid executable')
    errors = mod.verify(target)
    assert any('SHA256 mismatch' in err for err in errors)


def test_android_ci_invokes_verified_wrapper():
    workflow = (ROOT / '.github/workflows/android-build.yml').read_text(encoding='utf-8')
    assert 'python3 tools/verify_gradle_wrapper.py' in workflow
    assert './gradlew --no-daemon --stacktrace :app:assembleProbeDebug' in workflow
    assert 'gradle --no-daemon --stacktrace :app:assembleProbeDebug' not in workflow.replace('./gradlew --no-daemon --stacktrace :app:assembleProbeDebug', '')


def test_distribution_pinned_official_checksum():
    prop = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text(encoding='utf-8')
    assert 'gradle-9.3.1-bin.zip' in prop
    assert get_verifier().ZIP_SHA256 in prop
