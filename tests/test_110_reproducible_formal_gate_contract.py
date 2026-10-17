from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / 'tools/run_110_formal_source_gate.sh'
COMPILE = ROOT / 'tools/compile_api37_contract.sh'
LINT = ROOT / 'tools/lint_newapi_min26.sh'


def test_formal_source_gate_has_one_reproducible_entrypoint():
    assert GATE.exists()
    text = GATE.read_text()
    assert 'python3 -m pytest -q' in text
    assert 'bash tools/run_all_host_tests.sh' in text
    assert 'node tools/verify_input_router_node.js app/src/main/assets/chatgpt_bridge/site_adapter.js app/src/main/assets/chatgpt_bridge/content.js' in text
    assert 'bash tools/compile_api37_contract.sh' in text
    assert 'bash tools/lint_newapi_min26.sh' in text


def test_api37_contract_compile_uses_explicit_toolchain_and_cleans_output():
    assert COMPILE.exists()
    text = COMPILE.read_text()
    assert 'TOOLKIT_ROOT' in text
    assert 'jdk-17/bin/javac' in text
    assert 'sdk/platforms/android-37/android.jar' in text
    assert '-encoding UTF-8' in text
    assert "find \"$SRC\" -type f -name '*.java'" in text
    assert 'trap' in text


def test_newapi_lint_gate_uses_official_lint_with_explicit_sdk_floor():
    assert LINT.exists()
    text = LINT.read_text()
    assert '--check NewApi' in text
    assert 'android:minSdkVersion=\"26\"' in text
    assert '--compile-sdk-version 37' in text
    assert '--classpath' in text
    assert 'No issues found' not in text
