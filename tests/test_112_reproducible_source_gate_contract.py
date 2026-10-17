from pathlib import Path
from _project_paths import PROJECT_ROOT

SCRIPT = PROJECT_ROOT / 'tools' / 'run_112_rc2_source_gate.sh'


def test_rc2_source_gate_runs_every_required_layer_and_gecko153_abi():
    assert SCRIPT.exists()
    text = SCRIPT.read_text(encoding='utf-8')
    for needle in (
        'python3 -m pytest -q',
        'run_all_host_tests.sh',
        'verify_input_router_node.js',
        'verify_effect_verification_node.js',
        'compile_api37_contract.sh',
        'lint_newapi_min26.sh',
        'verify_geckoview153_abi.sh',
    ):
        assert needle in text
    assert 'BASELINE_APK' in text
