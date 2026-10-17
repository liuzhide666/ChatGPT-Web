"""Tests for no-subdirectory, hash-verifiable source delivery."""
import csv
import importlib.util
from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('flat_exporter', ROOT/'tools/export_flat_source.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def fixture_repo(tmp_path):
    root = tmp_path/'repo';root.mkdir()
    subprocess.run(['git','init','-q',str(root)],check=True)
    (root/'a').mkdir(); (root/'b').mkdir()
    (root/'a'/'same.txt').write_text('alpha')
    (root/'b'/'same.txt').write_text('beta')
    (root/'runner.sh').write_text('#!/bin/sh\necho okay\n')
    (root/'runner.sh').chmod(0o755)
    (root/'ChatGPT-Web-ALL-FILES-FLAT-2026-10-17.zip').write_bytes(b'legacy')
    subprocess.run(['git','-C',str(root),'add','.'],check=True)
    return root


def test_flat_export_restores_nested_source_and_modes(tmp_path):
    root = fixture_repo(tmp_path)
    output=tmp_path/'flat'
    result=mod.export(root,output)
    assert result == {'project_files':3,'root_entries':7}
    assert all(x.is_file() and '/' not in x.name for x in output.iterdir())
    assert not any(x.read_bytes()==b'legacy' for x in output.iterdir())
    rows=list(csv.DictReader((output/'FILE-MAP.csv').open()))
    assert len(set(x['flat_name'] for x in rows))==3
    assert any(x['git_mode']=='100755' for x in rows)
    subprocess.run(['python3',str(output/'restore_flat.py'),str(output),str(tmp_path/'restored')],check=True)
    assert (tmp_path/'restored'/'ChatGPT-Web/a/same.txt').read_text()=='alpha'
    assert (tmp_path/'restored'/'ChatGPT-Web/b/same.txt').read_text()=='beta'
    assert (tmp_path/'restored'/'ChatGPT-Web/runner.sh').stat().st_mode & 0o111


def test_rejects_reusing_export_destination(tmp_path):
    root=fixture_repo(tmp_path);output=tmp_path/'flat'
    mod.export(root,output)
    with pytest.raises(ValueError):mod.export(root,output)


def test_export_refuses_symlinks(tmp_path):
    root=fixture_repo(tmp_path)
    (root/'link').symlink_to('runner.sh')
    subprocess.run(['git','-C',str(root),'add','link'],check=True)
    with pytest.raises(ValueError):mod.export(root,tmp_path/'out')
