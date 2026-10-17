from _project_paths import APP_ROOT

JAVA = APP_ROOT / 'src/main/java/com/liuchenlin/chatgptweb'
ASSETS = APP_ROOT / 'src/main/assets/chatgpt_bridge'
MAIN = (JAVA / 'MainActivity.java').read_text()
VIEW = (JAVA / 'ChatGPTGeckoView.java').read_text()
BRIDGE = (JAVA / 'ChatGPTContentBridge.java').read_text()
CONTENT = (ASSETS / 'content.js').read_text()
SITE = (ASSETS / 'site_adapter.js').read_text()

REMOVED = [
    'NativeKeyIngress.java', 'InputIntentTransaction.java', 'InputEventDeduplicator.java',
    'ComposerInputRouter.java', 'ChatGPTInputConnection.java', 'InputRoutingState.java',
    'InputTraceBuffer.java', 'InputDiagnosticsDialog.java', 'ImeActionPolicy.java'
]


def assert_removed_input_architecture():
    for name in REMOVED:
        assert not (JAVA / name).exists(), name


def assert_native_transparent():
    assert_removed_input_architecture()
    combined = MAIN + '\n' + VIEW
    for token in ['KEYCODE_ENTER', 'NativeKeyIngress', 'ComposerInputRouter', 'InputIntentTransaction',
                  'InputEventDeduplicator', 'ChatGPTInputConnection', 'nativeLineBreakButton',
                  'executeNativeInputAction', 'PendingInputCommand', 'showInputDiagnostics']:
        assert token not in combined, token
    assert 'dispatchKeyEvent(' not in MAIN or 'return super.dispatchKeyEvent(event);' in MAIN
    assert 'extends GeckoView' in VIEW
    assert 'dispatchKeyEvent(' not in VIEW
    assert 'onCreateInputConnection(' not in VIEW
    assert 'new KeyEvent(' not in combined
    assert 'KeyEvent.change' not in combined


def assert_bridge_passive():
    for token in ['SUBMIT_COMPOSER', 'INSERT_LINE_BREAK', 'COMMAND_ACK', 'EDITOR_EFFECT', 'INPUT_ROUTE',
                  'requestComposerSubmit', 'requestComposerLineBreak', 'requestSemanticCommand']:
        assert token not in BRIDGE, token
    assert 'public enum EventType { READY, PONG, COMPOSER_STATE }' in BRIDGE
    assert 'requestPing()' in BRIDGE
    assert 'requestComposerState()' in BRIDGE


def assert_web_passive():
    for token in ['"keydown"', '"beforeinput"', 'SUBMIT_COMPOSER', 'INSERT_LINE_BREAK',
                  'reportInputRoute', 'observeEditorEffect', 'adapter.insertLineBreak', 'adapter.submit']:
        assert token not in CONTENT, token
    for token in ['const submit =', 'const insertLineBreak =', 'const observeEditorEffect =',
                  'execCommand(', '.click()']:
        assert token not in SITE, token
    assert 'ChatGPTWebSiteAdapter' in SITE
    assert 'snapshot' in SITE
    assert 'PING' in CONTENT and 'PONG' in CONTENT
