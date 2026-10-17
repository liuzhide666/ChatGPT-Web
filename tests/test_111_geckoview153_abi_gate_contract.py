from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SCRIPT=ROOT/'tools/verify_geckoview153_abi.sh'
DEX_BUILDER=ROOT/'tools/build_111_release_dex.sh'

def test_geckoview153_abi_gate_covers_formal_runtime_callbacks():
    assert SCRIPT.exists()
    text=SCRIPT.read_text()
    assert 'onPortMessage:(Ljava/lang/Object;Lorg/mozilla/geckoview/WebExtension$Port;)V' in text
    assert 'onPaintStatusReset:(Lorg/mozilla/geckoview/GeckoSession;)V' in text
    assert 'onSlowScript:(Lorg/mozilla/geckoview/GeckoSession;Ljava/lang/String;)Lorg/mozilla/geckoview/GeckoResult;' in text
    assert 'SlowScriptResponse;.CONTINUE' in text


def test_release_dex_builder_runs_actual_geckoview_abi_gate():
    text=DEX_BUILDER.read_text()
    assert 'verify_geckoview153_abi.sh' in text
