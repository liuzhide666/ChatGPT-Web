from _vc113_keyboard_neutrality import assert_native_transparent, assert_bridge_passive

def test_hardware_linebreak_is_no_longer_classified_or_executed_by_native():
    assert_native_transparent(); assert_bridge_passive()
