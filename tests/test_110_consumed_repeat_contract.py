from _vc113_keyboard_neutrality import assert_native_transparent

def test_shell_no_longer_consumes_repeat_or_matching_keyup():
    assert_native_transparent()
