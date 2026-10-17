from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_vc113_release_scripts_exist_and_are_version_locked():
    dex = ROOT / 'tools/build_113_release_dex.sh'
    apk = ROOT / 'tools/build_113_rc3_candidate.sh'
    assert dex.exists(), 'vc113 DEX builder missing'
    assert apk.exists(), 'vc113 APK builder missing'
    dt = dex.read_text(encoding='utf-8')
    at = apk.read_text(encoding='utf-8')
    assert 'VC113' in dt and 'VC112_RELEASE_DEX_OK' not in dt
    assert '--version-code 113' in at
    assert "versionCode='113'" in at
    assert 'VC113' in at and 'VC112_RC2_BUILD_OK' not in at


def test_vc113_candidate_builder_replaces_only_expected_payload_entries():
    text = (ROOT / 'tools/build_113_rc3_candidate.sh').read_text(encoding='utf-8')
    expected = {
        "'AndroidManifest.xml'",
        "'classes3.dex'",
        "'assets/chatgpt_bridge/manifest.json'",
        "'assets/chatgpt_bridge/site_adapter.js'",
        "'assets/chatgpt_bridge/content.js'",
    }
    for entry in expected:
        assert entry in text
    assert 'META-INF/' in text
    assert '--v1-signing-enabled false' in text
    assert '--v2-signing-enabled true' in text
    assert '--v3-signing-enabled true' in text
    assert 'zipalign" -P 16' in text


def test_part3_release_preserves_keyboard_neutrality_and_part2_gate():
    gate = ROOT / 'tools/run_113_part2_source_gate.sh'
    neutral = ROOT / 'tests/test_113_keyboard_neutrality_contract.py'
    assert gate.exists() and neutral.exists()

def test_part3_source_gate_exists_and_covers_release_tree():
    gate = ROOT / 'tools/run_113_part3_source_gate.sh'
    assert gate.exists(), 'Part-3 source gate missing'
    text = gate.read_text(encoding='utf-8')
    for token in ['pytest', 'run_all_host_tests.sh', 'verify_keyboard_neutrality_node.js',
                  'compile_api37_contract.sh', 'lint_newapi_min26.sh', 'verify_geckoview153_abi.sh']:
        assert token in text
    assert 'CHATGPT_WEB_1_1_0_RC3_VC113_PART3_SOURCE_GATE_OK' in text
