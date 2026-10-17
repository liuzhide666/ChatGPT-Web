from pathlib import Path
from _project_paths import PROJECT_ROOT

SCRIPT = PROJECT_ROOT / 'tools' / 'build_111_formal_candidate.sh'


def test_candidate_builder_is_minimal_signed_vc111_repack():
    assert SCRIPT.exists()
    text = SCRIPT.read_text()
    assert 'patch_release_manifest.py' in text
    assert '--version-code 111' in text
    assert 'classes3.dex' in text
    assert 'assets/chatgpt_bridge/content.js' in text
    assert 'zipalign' in text
    assert 'apksigner' in text
    assert '--v2-signing-enabled true' in text
    assert '--v3-signing-enabled true' in text
    assert 'META-INF/' in text
