"""Checks the integrity of Gradle artifact verification controls."""
import importlib.util
import pytest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / 'tools/verify_gradle_dependencies.py'


def tool():
    spec = importlib.util.spec_from_file_location('dependency_verify', MODULE)
    assert spec and spec.loader
    instance = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(instance)
    return instance


def payload(n=10, bypass=False):
    components = '\n'.join(
        f'<component group="demo.example" name="lib{i}" version="1.0"><artifact name="lib{i}.jar"><sha256 value="{"a"*64}"/></artifact></component>'
        for i in range(n)
    )
    exemption = '<trusted-artifacts><trust group="*"/></trusted-artifacts>' if bypass else ''
    return ('<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">'
            f'<configuration><verify-metadata>true</verify-metadata></configuration>{exemption}'
            f'<components>{components}</components></verification-metadata>')


def test_checksum_manifest_structure(tmp_path):
    path = tmp_path / 'verification-metadata.xml'
    path.write_text(payload(), encoding='utf-8')
    assert tool().inspect(path) == (10, [])


def test_rejects_all_trust_bypass(tmp_path):
    path = tmp_path / 'manifest.xml'
    path.write_text(payload(bypass=True), encoding='utf-8')
    assert 'trusted-artifacts' in ' '.join(tool().inspect(path)[1])


def test_rejects_incomplete_checksums(tmp_path):
    path = tmp_path / 'manifest.xml'
    path.write_text(payload().replace('a' * 64, 'bad', 1), encoding='utf-8')
    count, errors = tool().inspect(path)
    assert count == 9 and errors


def test_rejects_duplicate_component_and_dynamic_version(tmp_path):
    path = tmp_path / 'manifest.xml'
    path.write_text(payload().replace('name="lib1" version="1.0"', 'name="lib0" version="1.0"'), encoding='utf-8')
    assert any('duplicate' in e for e in tool().inspect(path)[1])
    path.write_text(payload().replace('version="1.0"', 'version="1.+"'), encoding='utf-8')
    assert any('non-static' in e for e in tool().inspect(path)[1])


def test_workflow_must_gate_real_build():
    if not (ROOT / 'gradle/verification-metadata.xml').exists():
        pytest.skip('waiting for reviewed CI-generated SHA-256 baseline')
    workflow = (ROOT / '.github/workflows/android-build.yml').read_text(encoding='utf-8')
    assert 'python3 tools/verify_gradle_dependencies.py' in workflow
    assert '--write-verification-metadata' not in workflow
