from _project_paths import APP_ROOT

MAIN=(APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text()
STRINGS=(APP_ROOT/'src/main/res/values/strings.xml').read_text()

def test_native_linebreak_fallback_is_completely_removed_without_resource_table_changes():
    assert 'native_line_break' not in STRINGS
    assert 'R.string.native_line_break' not in MAIN
    assert 'button.setText("换行")' not in MAIN
    assert '在 ChatGPT 输入框中插入换行' not in MAIN
