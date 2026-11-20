#!/usr/bin/env python3
"""Print SHA-256 of APK v2/v3 leaf signing certificate (not APK file hash).

Read-only; no private key, Android permission or network access required.
"""
import hashlib
import struct
import sys
from pathlib import Path

MAGIC = b"APK Sig Block 42"
IDS = (0xF05368C0, 0x7109871A)  # v3, v2


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def u64(data, offset):
    return struct.unpack_from("<Q", data, offset)[0]


def take_lp32(data, offset):
    size = u32(data, offset)
    start = offset + 4
    end = start + size
    if end > len(data):
        raise ValueError("Truncated APK signing structure")
    return data[start:end], end


def signing_cert_hash(path):
    data = Path(path).read_bytes()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0 or eocd + 22 > len(data):
        raise ValueError("APK ZIP directory missing")
    central_dir = u32(data, eocd + 16)
    if central_dir < 24 or data[central_dir - 16:central_dir] != MAGIC:
        raise ValueError("APK v2/v3 signing block missing")
    block_size = u64(data, central_dir - 24)
    start = central_dir - (block_size + 8)
    if start < 0 or u64(data, start) != block_size:
        raise ValueError("Malformed APK signing block")
    entries = {}
    offset = start + 8
    while offset < central_dir - 24:
        size = u64(data, offset)
        if size < 4 or offset + 8 + size > central_dir - 24:
            raise ValueError("Malformed signing entry")
        scheme = u32(data, offset + 8)
        entries[scheme] = data[offset + 12:offset + 8 + size]
        offset += size + 8
    for scheme in IDS:
        if scheme not in entries:
            continue
        signers, _ = take_lp32(entries[scheme], 0)
        signer, _ = take_lp32(signers, 0)
        signed_data, _ = take_lp32(signer, 0)
        _digests, pos = take_lp32(signed_data, 0)
        certificates, _ = take_lp32(signed_data, pos)
        cert, _ = take_lp32(certificates, 0)
        if not cert:
            raise ValueError("Empty signing certificate")
        return hashlib.sha256(cert).hexdigest()
    raise ValueError("No APK v2/v3 signature found")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 scripts/guardian-apk-cert.py path/to/app.apk")
    try:
        print(signing_cert_hash(sys.argv[1]))
    except (OSError, ValueError, struct.error) as exc:
        sys.exit(f"Cannot read APK signing certificate: {exc}")
