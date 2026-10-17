#!/usr/bin/env python3
"""Patch an already-merged binary AndroidManifest.xml for a formal ChatGPT Web release.

This preserves the GeckoView/AndroidX merged component graph byte-for-byte except for the
manifest fields intentionally changed here: versionCode, versionName and application.debuggable.
"""
from __future__ import annotations

import argparse
import struct
from pathlib import Path

RES_XML_TYPE = 0x0003
RES_STRING_POOL_TYPE = 0x0001
RES_XML_START_ELEMENT_TYPE = 0x0102
UTF8_FLAG = 0x100
TYPE_INT_DEC = 0x10
TYPE_INT_BOOLEAN = 0x12


def u16(data: bytes | bytearray, offset: int) -> int:
    return struct.unpack_from('<H', data, offset)[0]


def u32(data: bytes | bytearray, offset: int) -> int:
    return struct.unpack_from('<I', data, offset)[0]


def p32(data: bytearray, offset: int, value: int) -> None:
    struct.pack_into('<I', data, offset, value & 0xFFFFFFFF)


def dec8len(data: bytes, offset: int) -> tuple[int, int]:
    value = data[offset]
    offset += 1
    if value & 0x80:
        return ((value & 0x7F) << 8) | data[offset], offset + 1
    return value, offset


def enc8len(value: int) -> bytes:
    if value > 0x7FFF:
        raise ValueError('UTF-8 string length too large')
    return bytes([(value >> 8) | 0x80, value & 0xFF]) if value > 0x7F else bytes([value])


def dec16len(data: bytes, offset: int) -> tuple[int, int]:
    value = u16(data, offset)
    offset += 2
    if value & 0x8000:
        return ((value & 0x7FFF) << 16) | u16(data, offset), offset + 2
    return value, offset


def enc16len(value: int) -> bytes:
    if value > 0x7FFFFFFF:
        raise ValueError('UTF-16 string length too large')
    if value > 0x7FFF:
        return struct.pack('<HH', (value >> 16) | 0x8000, value & 0xFFFF)
    return struct.pack('<H', value)


def parse_pool(data: bytes, offset: int) -> dict:
    chunk_type, header_size, chunk_size = struct.unpack_from('<HHI', data, offset)
    if chunk_type != RES_STRING_POOL_TYPE or header_size < 28:
        raise ValueError('binary XML string pool missing')
    count, style_count, flags, strings_start, styles_start = struct.unpack_from('<IIIII', data, offset + 8)
    strings: list[str] = []
    for index in range(count):
        pos = offset + strings_start + u32(data, offset + header_size + index * 4)
        if flags & UTF8_FLAG:
            _, pos = dec8len(data, pos)
            byte_len, pos = dec8len(data, pos)
            strings.append(data[pos:pos + byte_len].decode('utf-8'))
        else:
            unit_len, pos = dec16len(data, pos)
            strings.append(data[pos:pos + unit_len * 2].decode('utf-16le'))
    return {
        'header_size': header_size,
        'chunk_size': chunk_size,
        'count': count,
        'style_count': style_count,
        'flags': flags,
        'strings_start': strings_start,
        'styles_start': styles_start,
        'strings': strings,
    }


def rebuild_pool(data: bytes, offset: int, replacements: dict[str, str]) -> tuple[bytes, list[str]]:
    meta = parse_pool(data, offset)
    strings = [replacements.get(value, value) for value in meta['strings']]
    utf8 = bool(meta['flags'] & UTF8_FLAG)
    offsets: list[int] = []
    encoded: list[bytes] = []
    cursor = 0
    for value in strings:
        offsets.append(cursor)
        if utf8:
            raw = value.encode('utf-8')
            units = len(value.encode('utf-16le')) // 2
            blob = enc8len(units) + enc8len(len(raw)) + raw + b'\0'
        else:
            raw = value.encode('utf-16le')
            blob = enc16len(len(raw) // 2) + raw + b'\0\0'
        encoded.append(blob)
        cursor += len(blob)
    string_blob = b''.join(encoded)
    string_blob += b'\0' * ((-len(string_blob)) % 4)

    old_end = offset + meta['chunk_size']
    old_styles_abs = offset + meta['styles_start'] if meta['styles_start'] else old_end
    styles = data[old_styles_abs:old_end] if meta['styles_start'] else b''
    prefix = bytearray(data[offset:offset + meta['strings_start']])
    for index, string_offset in enumerate(offsets):
        p32(prefix, meta['header_size'] + index * 4, string_offset)
    new_styles_start = meta['strings_start'] + len(string_blob) if meta['styles_start'] else 0
    p32(prefix, 24, new_styles_start)
    chunk = prefix + string_blob + styles
    p32(chunk, 4, len(chunk))
    return data[:offset] + bytes(chunk) + data[old_end:], strings


def patch_release_manifest(data: bytes, version_code: int, old_version_name: str, version_name: str) -> bytes:
    xml_type, header_size, total_size = struct.unpack_from('<HHI', data, 0)
    if xml_type != RES_XML_TYPE or total_size != len(data):
        raise ValueError('not a complete binary AndroidManifest.xml')

    pool_offset = header_size
    old_pool = parse_pool(data, pool_offset)
    if old_version_name not in old_pool['strings']:
        raise ValueError(f'old versionName not found: {old_version_name}')
    data, strings = rebuild_pool(data, pool_offset, {old_version_name: version_name})
    new_pool = parse_pool(data, pool_offset)
    out = bytearray(data)
    p32(out, 4, len(out))
    data = bytes(out)

    version_code_patched = False
    debuggable_patched = False
    application_seen = False
    offset = header_size + new_pool['chunk_size']
    while offset + 8 <= len(data):
        chunk_type, chunk_header_size, chunk_size = struct.unpack_from('<HHI', data, offset)
        if chunk_size < chunk_header_size or offset + chunk_size > len(data):
            raise ValueError('invalid binary XML chunk')
        if chunk_type == RES_XML_START_ELEMENT_TYPE:
            ext = offset + chunk_header_size
            element_name_index = u32(data, ext + 4)
            element_name = strings[element_name_index] if element_name_index < len(strings) else ''
            attr_start = u16(data, ext + 8)
            attr_size = u16(data, ext + 10)
            attr_count = u16(data, ext + 12)
            attr_base = ext + attr_start
            mutable = bytearray(data)
            for index in range(attr_count):
                attr = attr_base + index * attr_size
                name_index = u32(data, attr + 4)
                name = strings[name_index] if name_index < len(strings) else ''
                data_type = data[attr + 15]
                if element_name == 'manifest' and name == 'versionCode':
                    if data_type != TYPE_INT_DEC:
                        raise ValueError('versionCode is not an integer attribute')
                    p32(mutable, attr + 16, version_code)
                    version_code_patched = True
                elif element_name == 'application' and name == 'debuggable':
                    application_seen = True
                    if data_type != TYPE_INT_BOOLEAN:
                        raise ValueError('debuggable is not TYPE_INT_BOOLEAN')
                    p32(mutable, attr + 16, 0)
                    debuggable_patched = True
            data = bytes(mutable)
        offset += chunk_size

    if not version_code_patched:
        raise ValueError('versionCode attribute not patched')
    if not application_seen or not debuggable_patched:
        raise ValueError('debuggable attribute not patched')
    if version_name not in parse_pool(data, pool_offset)['strings']:
        raise ValueError('versionName string not patched')
    return data


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest-in', required=True)
    parser.add_argument('--manifest-out', required=True)
    parser.add_argument('--version-code', required=True, type=int)
    parser.add_argument('--old-version-name', required=True)
    parser.add_argument('--version-name', required=True)
    args = parser.parse_args()
    patched = patch_release_manifest(
        Path(args.manifest_in).read_bytes(),
        args.version_code,
        args.old_version_name,
        args.version_name,
    )
    Path(args.manifest_out).write_bytes(patched)


if __name__ == '__main__':
    main()
