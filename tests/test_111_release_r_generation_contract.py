import importlib.util
from pathlib import Path
from _project_paths import PROJECT_ROOT

SCRIPT = PROJECT_ROOT / 'tools' / 'generate_release_r.py'


def load_module():
    spec = importlib.util.spec_from_file_location('generate_release_r', SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_release_r_generator_resolves_real_ids_from_aapt_resource_dump():
    assert SCRIPT.exists()
    mod = load_module()
    dump = '''
      spec resource 0x7f050030 pkg:id/gecko_container: flags=0x00000000
      spec resource 0x7f05003f pkg:id/recovery_retry: flags=0x00000000
      spec resource 0x7f070000 pkg:layout/activity_main: flags=0x00000000
      spec resource 0x7f07000a pkg:layout/view_recovery: flags=0x00000000
    '''
    refs = {('id','gecko_container'), ('id','recovery_retry'), ('layout','activity_main'), ('layout','view_recovery')}
    ids = mod.extract_resource_ids(dump, refs)
    assert ids[('layout','activity_main')] == 0x7F070000
    assert ids[('layout','view_recovery')] == 0x7F07000A
    assert ids[('id','gecko_container')] == 0x7F050030
    assert ids[('id','recovery_retry')] == 0x7F05003F


def test_generated_r_uses_real_hex_constants_not_host_test_sequential_ids():
    assert SCRIPT.exists()
    mod = load_module()
    ids = {('layout','activity_main'):0x7F070000, ('id','gecko_container'):0x7F050030}
    text = mod.render_r_java('com.liuchenlin.chatgptweb', ids)
    assert '0x7f070000' in text.lower()
    assert '0x7f050030' in text.lower()
    assert 'activity_main = 1' not in text
    assert 'gecko_container = 4' not in text
