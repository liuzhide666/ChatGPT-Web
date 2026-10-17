from _project_paths import TOOLS_ROOT


def test_all_java_gate_javac_invocations_are_utf8_explicit():
    for name in ['run_all_host_tests.sh','compile_api37_contract.sh','build_111_release_dex.sh']:
        text=(TOOLS_ROOT/name).read_text()
        assert '-encoding UTF-8' in text, f'{name} must not depend on host locale'
