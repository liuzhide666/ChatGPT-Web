from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CTRL=(ROOT/'app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java').read_text()
MAIN=(ROOT/'app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text()


def test_gecko_paint_reset_and_slow_script_are_generation_scoped_evidence_only():
    assert 'onPaintStatusReset(GeckoSession session)' in CTRL
    assert 'onSlowScript(' in CTRL and 'GeckoSession session, String scriptFileName)' in CTRL
    assert 'healthObserver.onPaintStatusReset(navigationGeneration' in CTRL
    assert 'healthObserver.onSlowScript(navigationGeneration' in CTRL
    assert 'SlowScriptResponse.CONTINUE' in CTRL
    assert 'onPaintStatusReset(long generation, long nowMs)' in CTRL
    assert 'onSlowScript(long generation, long nowMs)' in CTRL
    assert 'onPaintStatusReset(long generation, long nowMs)' in MAIN
    assert 'onSlowScript(long generation, long nowMs)' in MAIN
    for marker in ('onPaintStatusReset(GeckoSession session)', 'onSlowScript('):
        body=CTRL.split(marker,1)[1].split('\n    }',1)[0]
        assert '.reload(' not in body and 'hardRecoverTrustedPage' not in body and 'showPageHealthRecoveryUi' not in body
