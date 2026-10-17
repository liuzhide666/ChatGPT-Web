from _vc113_keyboard_neutrality import assert_native_transparent, assert_bridge_passive, assert_web_passive

def test_former_native_input_contract_is_replaced_by_end_to_end_transparency():
    assert_native_transparent(); assert_bridge_passive(); assert_web_passive()
