from _vc113_keyboard_neutrality import assert_native_transparent, SITE

def test_native_composition_wrapper_is_removed_and_web_state_is_observation_only():
    assert_native_transparent()
    assert 'composing' in SITE and 'snapshot' in SITE
