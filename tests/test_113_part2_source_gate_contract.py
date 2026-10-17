from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
GATE=ROOT/'tools/run_113_part2_source_gate.sh'

def test_part2_source_gate_exists_and_runs_all_required_layers():
    assert GATE.exists()
    text=GATE.read_text()
    for token in [
        'python3 -m pytest', 'run_all_host_tests.sh', 'verify_keyboard_neutrality_node.js',
        'compile_api37_contract.sh', 'lint_newapi_min26.sh', 'verify_geckoview153_abi.sh',
        'CHATGPT_WEB_1_1_0_RC3_VC113_PART2_SOURCE_GATE_OK'
    ]:
        assert token in text
    assert 'verify_input_router_node.js' not in text
    assert 'verify_effect_verification_node.js' not in text
