from _vc113_keyboard_neutrality import assert_native_transparent, assert_bridge_passive, assert_web_passive, CONTENT

def test_vc103_input_router_is_superseded_by_keyboard_transparency():
    assert_native_transparent(); assert_bridge_passive(); assert_web_passive()

def test_passive_composer_state_remains_boolean_deduplicated():
    assert 'lastComposerStateKey' in CONTENT and 'composerStateKey' in CONTENT
    assert 'if (!force && key === lastComposerStateKey) return' in CONTENT
