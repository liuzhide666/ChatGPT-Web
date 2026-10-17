"""Diagnostic ABI split must never silently mutate the formal distribution."""
from pathlib import Path
import importlib.util
import zipfile
import pytest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('arm64_probe', ROOT / 'tools/verify_probe_arm64.py')
assert SPEC and SPEC.loader
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)


def fake(path, abis):
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('classes.dex', b'100' * 100)
        for abi in abis:
            z.writestr('lib/' + abi + '/libtest.so', b'fake-elf' * 1000)


def test_opt_in_limited_to_explicit_gradle_init_script():
    source = (ROOT/'app/build.gradle').read_text(encoding='utf-8')
    init = (ROOT/'tools/arm64-probe-only.init.gradle').read_text(encoding='utf-8')
    ci = (ROOT/'.github/workflows/abi-probe-experiment.yml').read_text(encoding='utf-8')
    assert 'abiFilters' not in source
    assert "getByName('probe').ndk.abiFilters.add('arm64-v8a')" in init
    assert 'finalizeDsl' in init and 'beforeProject' in init
    assert '-I tools/arm64-probe-only.init.gradle' in ci
    assert 'versionCode 113' in source


def test_experiment_accepts_three_abi_reference(tmp_path):
    control = tmp_path / 'universal.apk'
    narrow = tmp_path / 'arm64.apk'
    fake(control, ['arm64-v8a','armeabi-v7a','x86_64'])
    fake(narrow, ['arm64-v8a'])
    result = mod.compare(control,narrow)
    assert result['bytes_saved'] > 0 and result['arm64']['abis'] == {'arm64-v8a':1}
    assert result['device_compatibility'] == 'NOT_RUN'


def test_rejects_wrong_or_missing_abi(tmp_path):
    control = tmp_path / 'u.apk'
    wrong = tmp_path / 'n.apk'
    fake(control,['arm64-v8a','armeabi-v7a','x86_64'])
    fake(wrong,['arm64-v8a','armeabi-v7a'])
    with pytest.raises(ValueError,match='not exactly ARM64'):
        mod.compare(control, wrong)


def test_rejects_unrepresentative_control(tmp_path):
    control = tmp_path / 'u.apk'
    narrow = tmp_path / 'n.apk'
    fake(control,['arm64-v8a'])
    fake(narrow,['arm64-v8a'])
    with pytest.raises(ValueError,match='3 expected ABIs'):
        mod.compare(control,narrow)
