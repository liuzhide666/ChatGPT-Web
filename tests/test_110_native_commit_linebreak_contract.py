from _vc113_keyboard_neutrality import assert_native_transparent, assert_web_passive

def test_newline_commit_and_dom_beforeinput_are_not_shell_semantic_channels():
    assert_native_transparent(); assert_web_passive()
