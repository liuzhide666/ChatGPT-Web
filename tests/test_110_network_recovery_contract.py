from _project_paths import APP_ROOT
JAVA=APP_ROOT/'src/main/java/com/liuchenlin/chatgptweb'
MONITOR=JAVA/'NetworkStateMonitor.java'
HEALTH=(JAVA/'BrowserHealthSupervisor.java').read_text()
MAIN=(JAVA/'MainActivity.java').read_text()
MANIFEST=(APP_ROOT/'src/main/AndroidManifest.xml').read_text()


def test_network_monitor_uses_default_network_callback_and_is_lifecycle_bounded():
    assert MONITOR.exists()
    text=MONITOR.read_text()
    assert 'registerDefaultNetworkCallback' in text and 'unregisterNetworkCallback' in text
    assert 'mayHaveNetwork' in text and 'getActiveNetwork()' in text and 'ACCESS_NETWORK_STATE' in MANIFEST


def test_health_supervisor_blocks_destructive_recovery_when_known_offline():
    assert 'networkAvailable' in HEALTH and '!s.networkAvailable' in HEALTH
    assert 'Decision.REBIND_BRIDGE' in HEALTH and 'Long.MAX_VALUE' in HEALTH


def test_network_transition_updates_snapshot_without_automatic_page_or_bridge_action():
    assert 'NetworkStateMonitor' in MAIN and 'onNetworkAvailabilityChanged' in MAIN
    body=MAIN.split('private void onNetworkAvailabilityChanged',1)[1].split('private void onRecoveryBlockerChanged',1)[0]
    assert 'rebindCurrentSession' not in body and 'requestPing' not in body
    assert 'resetIncident' not in body
    assert 'evaluateBrowserHealth' in body and 'networkStateMonitor.mayHaveNetwork()' in MAIN
