import json
from pathlib import Path
from _project_paths import APP_ROOT, PROJECT_ROOT, TOOLS_ROOT
ROOT = APP_ROOT
G=(ROOT/'build.gradle').read_text()
M=json.loads((ROOT/'src/main/assets/chatgpt_bridge/manifest.json').read_text())

def test_110_identity():
    assert 'versionCode 113' in G
    assert 'versionName "1.1.0"' in G
    assert M['version']=='1.1.2'
    assert M['content_scripts'][0]['js']==['site_adapter.js','content.js']
