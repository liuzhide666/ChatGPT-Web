import json
from pathlib import Path
from _vc113_keyboard_neutrality import assert_native_transparent, assert_bridge_passive, assert_web_passive
ROOT=Path(__file__).resolve().parents[1]
G=(ROOT/'app/build.gradle').read_text(); MAN=json.loads((ROOT/'app/src/main/assets/chatgpt_bridge/manifest.json').read_text())
CONTENT=(ROOT/'app/src/main/assets/chatgpt_bridge/content.js').read_text(); SITE=(ROOT/'app/src/main/assets/chatgpt_bridge/site_adapter.js').read_text(); MAIN=(ROOT/'app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java').read_text(); SUP=(ROOT/'app/src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java').read_text()

def test_current_rc3_identity():
    assert 'versionCode 113' in G and 'versionName "1.1.0"' in G and MAN['version']=='1.1.2'

def test_former_native_input_formal_contract_is_superseded_by_transparency():
    assert_native_transparent(); assert_bridge_passive(); assert_web_passive()

def test_default_cache_and_lifecycle_rules_remain():
    combined='\n'.join([CONTENT,SITE,MAIN,SUP])
    for forbidden in ['clearCache','CACHE_MODE_NO_STORE','WorkManager']:
        assert forbidden not in combined
    assert 'PAGE_HEALTH_POLL_MS' not in MAIN

def test_browser_health_rebind_precedes_reload_during_part1():
    assert 'REBIND_BRIDGE' in SUP and 'SOFT_RELOAD' in SUP and 'HARD_REPLACE' in SUP
    switch=MAIN.split('switch (decision)',1)[1].split('    }\n\n    @Override',1)[0]
    assert switch.index('REBIND_BRIDGE') < switch.index('SOFT_RELOAD') < switch.index('HARD_REPLACE')
