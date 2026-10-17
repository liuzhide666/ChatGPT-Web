from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROTOCOL = ROOT / 'docs' / '0.4.0-gecko-probe-device-test-protocol.md'
CAPTURE = ROOT / 'tools' / 'capture_probe_evidence.sh'


def test_probe_protocol_and_capture_script_exist():
    assert PROTOCOL.exists()
    assert CAPTURE.exists()


def test_protocol_uses_probe_package_and_recording_proof():
    text = PROTOCOL.read_text(encoding='utf-8')
    assert 'com.liuchenlin.chatgptweb.geckoprobe' in text
    assert 'SOURCE_MICROPHONE' in text
    assert 'RECORDING type=MICROPHONE status=RECORDING' in text
    assert 'GECKOVIEW_OFFLINE_OK' in text


def test_protocol_protects_private_content():
    text = PROTOCOL.read_text(encoding='utf-8').lower()
    for forbidden in ['please upload full logcat', 'please upload full ui tree', 'record conversation text']:
        assert forbidden not in text
    for required in ['do not upload full logcat', 'do not upload full ui tree', 'conversation text']:
        assert required in text


def test_capture_script_filters_to_project_tags_and_does_not_dump_ui_tree_or_screenshot():
    text = CAPTURE.read_text(encoding='utf-8')
    assert 'ChatGPTWeb/(Permission|Media|Session|Navigation|Recovery)' in text
    assert 'uiautomator dump' not in text
    assert 'screencap' not in text
    assert 'logcat -d' in text
