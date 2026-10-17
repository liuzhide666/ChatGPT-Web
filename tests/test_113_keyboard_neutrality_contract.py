from _project_paths import APP_ROOT

JAVA = APP_ROOT / 'src/main/java/com/liuchenlin/chatgptweb'
ASSETS = APP_ROOT / 'src/main/assets/chatgpt_bridge'

MAIN = (JAVA / 'MainActivity.java').read_text()
VIEW = (JAVA / 'ChatGPTGeckoView.java').read_text()
BRIDGE = (JAVA / 'ChatGPTContentBridge.java').read_text()
CONTENT = (ASSETS / 'content.js').read_text()
ADAPTER = (ASSETS / 'site_adapter.js').read_text()

SEMANTIC_CLASSES = [
    'NativeKeyIngress.java',
    'InputIntentTransaction.java',
    'InputEventDeduplicator.java',
    'ComposerInputRouter.java',
    'ChatGPTInputConnection.java',
    'InputRoutingState.java',
    'InputTraceBuffer.java',
    'InputDiagnosticsDialog.java',
]


def test_semantic_keyboard_subsystem_is_absent_from_production_tree():
    for name in SEMANTIC_CLASSES:
        assert not (JAVA / name).exists(), name


def test_activity_and_geckoview_do_not_interpret_enter_or_wrap_input_connection():
    joined = MAIN + '\n' + VIEW
    assert 'KEYCODE_ENTER' not in joined
    assert 'NativeKeyIngress' not in joined
    assert 'ComposerInputRouter' not in joined
    assert 'InputIntentTransaction' not in joined
    assert 'InputEventDeduplicator' not in joined
    assert 'ChatGPTInputConnection' not in VIEW
    assert 'setInputIntentDelegate' not in VIEW
    assert 'setInputConnectionObserver' not in VIEW
    assert 'routeEnter(' not in VIEW
    assert 'new KeyEvent(' not in joined
    assert 'KeyEvent.change' not in joined


def test_native_bridge_has_no_composer_semantic_command_channel():
    forbidden = [
        'SUBMIT_COMPOSER', 'INSERT_LINE_BREAK', 'COMMAND_ACK', 'EDITOR_EFFECT', 'INPUT_ROUTE',
        'requestComposerSubmit', 'requestComposerLineBreak', 'requestSemanticCommand',
    ]
    for token in forbidden:
        assert token not in BRIDGE, token
    assert 'PendingInputCommand' not in MAIN
    assert 'pendingInputCommands' not in MAIN
    assert 'executeNativeInputAction' not in MAIN
    assert 'nativeLineBreakButton' not in MAIN
    assert 'showInputDiagnostics' not in MAIN


def test_web_extension_does_not_capture_keyboard_or_execute_editor_actions():
    for token in ['"keydown"', '"beforeinput"', 'SUBMIT_COMPOSER', 'INSERT_LINE_BREAK',
                  'insertLineBreak(', 'reportInputRoute', 'observeEditorEffect']:
        assert token not in CONTENT, token
    for token in ['const submit =', 'const insertLineBreak =', 'const observeEditorEffect =',
                  'execCommand(', '.click()', 'send-button', 'composer-submit-button']:
        assert token not in ADAPTER, token


def test_editor_info_is_left_to_gecko_without_app_ime_policy():
    assert not (JAVA / 'ImeActionPolicy.java').exists()
    assert 'ImeActionPolicy' not in VIEW
