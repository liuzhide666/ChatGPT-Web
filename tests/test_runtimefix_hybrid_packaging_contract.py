from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text(encoding='utf-8')


def test_r0_startup_diagnostics_do_not_require_new_resource_ids():
    assert 'R.layout.view_startup_recovery' not in MAIN
    for name in [
        'startup_recovery_message', 'startup_stage', 'startup_failure_type',
        'startup_failure_message', 'startup_failure_cause', 'startup_retry'
    ]:
        assert f'R.id.{name}' not in MAIN


def test_r0_programmatic_diagnostic_view_is_built_in_application_code():
    assert 'buildStartupRecoveryView' in MAIN
    assert 'new TextView(this)' in MAIN
    assert 'new Button(this)' in MAIN


def test_r0_visible_chinese_copy_is_embedded_without_locale_sensitive_source_bytes():
    # Hybrid R0 compiles app-owned Java separately; use Unicode escapes so javac locale cannot corrupt copy.
    assert r'\u7f51\u9875\u5185\u6838\u542f\u52a8\u5931\u8d25' in MAIN  # 网页内核启动失败
    assert r'\u521d\u59cb\u5316\u7f51\u9875\u5185\u6838' in MAIN  # 初始化网页内核
