from importlib import util
from pathlib import Path
import struct
import zipfile
import pytest

SRC = Path(__file__).resolve().parents[1] / 'tools/analyze_probe_apk.py'
spec = util.spec_from_file_location('analyze_probe_apk', SRC)
mod = util.module_from_spec(spec)
spec.loader.exec_module(mod)


def elf_fixture(page):
    b = bytearray(128)
    b[:4] = b'\x7fELF'
    b[4] = 2  # ELF64
    b[5] = 1  # little-endian
    struct.pack_into('<Q', b, 32, 64)
    struct.pack_into('<HH', b, 54, 56, 1)
    struct.pack_into('<I', b, 64, 1)  # PT_LOAD
    struct.pack_into('<Q', b, 64+48, page)
    return bytes(b)


def test_reports_abi_categories_and_verified_elf_alignment(tmp_path):
    apk = tmp_path/'example.apk'
    with zipfile.ZipFile(apk,'w') as z:
        z.writestr('classes.dex', b'X'*31)
        z.writestr('lib/arm64-v8a/libok.so', elf_fixture(16384))
        z.writestr('lib/x86_64/libold.so', elf_fixture(4096))
    report=mod.analyze(apk)
    assert report['entry_count']==3
    assert report['categories']['native/arm64-v8a']['uncompressed']==128
    assert report['elf_pt_load_16k']=={'PASS':1,'FAIL':1,'NOT_VERIFIED':0}
    assert report['zipalign_16k']=='NOT_RUN'
    assert report['overall_16k_certified'] is False


def test_malformed_elf_is_not_assumed_16k(tmp_path):
    apk=tmp_path/'unknown.apk'
    with zipfile.ZipFile(apk,'w') as z:
        z.writestr('lib/armeabi-v7a/libunknown.so', b'not an ELF')
    result=mod.analyze(apk)
    assert result['elf_pt_load_16k']['NOT_VERIFIED']==1


def test_rejects_archive_traversal(tmp_path):
    apk=tmp_path/'unsafe.apk'
    with zipfile.ZipFile(apk,'w') as z:
        z.writestr('../mykey',b'x')
    with pytest.raises(ValueError):mod.analyze(apk)


def test_cli_reports_16k_as_device_uncertified():
    source=SRC.read_text()
    assert 'overall_16k_certified' in source
    assert 'zipalign_16k' in source
    assert "'-P', '16'" in source
