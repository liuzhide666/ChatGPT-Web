from _project_paths import APP_ROOT
CONTENT=(APP_ROOT/'src/main/assets/chatgpt_bridge/content.js').read_text()
HEALTH=(APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java').read_text()
MAIN=(APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text()


def test_web_bridge_has_no_permanent_heartbeat_timer():
    assert 'HEARTBEAT_MS' not in CONTENT
    bridge=(APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java').read_text()
    enum_body=bridge.split('public enum EventType',1)[1].split('}',1)[0]
    assert 'HEARTBEAT' not in enum_body and 'setInterval(' not in CONTENT and 'send("HEARTBEAT")' not in CONTENT


def test_probe_is_incident_driven_and_stable_has_no_deadline():
    assert 'State.PROBING' in HEALTH and 'PROBE_GRACE_MS' in HEALTH
    assert 'case PROBE:' in MAIN
    probe_body=MAIN.split('case PROBE:',1)[1].split('break;',1)[0]
    assert 'requestPing()' in probe_body
    assert 'rebindCurrentSession' not in probe_body and 'softRecoverCurrentPage' not in probe_body
    stable=HEALTH.split('case STABLE:',1)[1].split('case SUSPECT:',1)[0]
    assert 'Long.MAX_VALUE' in stable or 'return Decision.NONE' in stable


def test_paint_reset_and_slow_script_are_not_positive_freshness_or_reload_authority():
    reset=MAIN.split('onPaintStatusReset(long generation, long nowMs)',1)[1].split('\n                }',1)[0]
    slow=MAIN.split('onSlowScript(long generation, long nowMs)',1)[1].split('\n                }',1)[0]
    fcp=MAIN.split('onFirstContentfulPaint(long generation, long nowMs)',1)[1].split('\n                }',1)[0]
    assert 'paintValid = false' in reset and 'onPaintInvalid' in reset
    assert 'onSlowScript(nowMs)' in slow and 'softRecover' not in slow and 'hardRecover' not in slow
    assert 'paintValid = true' in fcp and 'onContentHealthy' in fcp
