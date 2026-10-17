from _project_paths import PROJECT_ROOT

SCRIPT = PROJECT_ROOT / 'tools/run_113_part1_source_gate.sh'


def test_part1_source_gate_is_keyboard_neutral_and_runs_full_compile_stack():
    assert SCRIPT.exists()
    text = SCRIPT.read_text()
    for token in [
        'python3 -m pytest -q',
        'run_all_host_tests.sh',
        'verify_keyboard_neutrality_node.js',
        'compile_api37_contract.sh',
        'lint_newapi_min26.sh',
        'verify_geckoview153_abi.sh',
        'CHATGPT_WEB_1_1_0_RC3_VC113_PART1_SOURCE_GATE_OK',
    ]:
        assert token in text
    assert 'verify_input_router_node.js' not in text
    assert 'verify_effect_verification_node.js' not in text
