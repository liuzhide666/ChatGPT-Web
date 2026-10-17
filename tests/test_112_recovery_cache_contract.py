from _project_paths import APP_ROOT

HEALTH=(APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java').read_text()
MAIN=(APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text()
CTRL=(APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java').read_text()


def test_composer_focus_is_a_destructive_recovery_blocker_but_not_probe_authority():
    assert 'composerFocused' in HEALTH
    assert 'destructiveBlocked' in HEALTH
    assert 'composerFocused' in MAIN
    assert 'composerFocused' in MAIN[MAIN.index('new BrowserHealthSupervisor.Snapshot'):]


def test_automatic_recovery_uses_default_cache_only():
    soft=CTRL.split('void softRecoverCurrentPage()',1)[1].split('\n    }',1)[0]
    hard=CTRL.split('void hardRecoverTrustedPage()',1)[1].split('\n    }',1)[0]
    assert 'session.reload();' in soft
    assert 'LOAD_FLAGS_BYPASS_CACHE' not in soft
    assert 'LOAD_FLAGS_BYPASS_CACHE' not in hard
    assert 'clearCache' not in CTRL and 'clearData' not in CTRL


def test_bypass_cache_is_available_only_from_explicit_recovery_ui_action():
    assert 'forceRefetchCurrentPage' in CTRL
    force=CTRL.split('void forceRefetchCurrentPage()',1)[1].split('\n    }',1)[0]
    assert 'LOAD_FLAGS_BYPASS_CACHE' in force
    assert '强制重新获取页面资源' in MAIN
    assert 'R.id.recovery_force_refetch' not in MAIN
    assert 'forceRefetchCurrentPage()' in MAIN
