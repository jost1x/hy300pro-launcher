#!/usr/bin/env python3
"""Make the HY300 Pro Android 11 SystemUI volume panel vertical and right-aligned."""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import re
import ssl
import struct
import subprocess
import tempfile
import urllib.request
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
SDK = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
SIGNER = SDK / "build-tools/35.0.0/apksigner"
AAPT = SDK / "build-tools/35.0.0/aapt"
ZIPALIGN = SDK / "build-tools/35.0.0/zipalign"
SOURCE = "https://android.googlesource.com/platform/build/+/refs/tags/android-11.0.0_r1/target/product/security/"
EXPECTED_CERT = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"
PACKAGE_ID = 0x7F
TYPE_INTEGER = 0x0B
ENTRY_VOLUME_DIALOG_GRAVITY = 0x66
GRAVITY_RIGHT_CENTER = 0x15
GRAVITY_BOTTOM_CENTER = 0x51
UI_MODE_TYPE_MASK = 0x0F
UI_MODE_TYPE_TELEVISION = 0x04
GENERIC_ROW = "res/layout/volume_dialog_row.xml"
TELEVISION_ROW = "res/layout-land-television-v8/volume_dialog_row.xml"


def fetch(name):
    with urllib.request.urlopen(SOURCE + name + "?format=TEXT", timeout=30) as response:
        return base64.b64decode(response.read())


def chunks(data, start, end):
    offset = start
    while offset < end:
        if offset + 8 > end:
            raise ValueError("Truncated Android resource chunk")
        chunk_type, header_size, chunk_size = struct.unpack_from("<HHI", data, offset)
        if header_size < 8 or chunk_size < header_size or offset + chunk_size > end:
            raise ValueError("Invalid Android resource chunk")
        yield offset, chunk_type, header_size, chunk_size
        offset += chunk_size


def patch_television_gravity(data):
    found_tv = 0
    found_default = 0
    output = bytearray(data)
    for package_offset, chunk_type, header_size, chunk_size in chunks(data, 12, len(data)):
        if chunk_type != 0x0200:  # RES_TABLE_PACKAGE_TYPE
            continue
        package_id = struct.unpack_from("<I", data, package_offset + 8)[0]
        if package_id != PACKAGE_ID:
            continue
        package_end = package_offset + chunk_size
        for type_offset, nested_type, type_header, type_size in chunks(
            data, package_offset + header_size, package_end
        ):
            if nested_type != 0x0201:  # RES_TABLE_TYPE_TYPE
                continue
            resource_type = data[type_offset + 8]
            entry_count = struct.unpack_from("<I", data, type_offset + 12)[0]
            entries_start = struct.unpack_from("<I", data, type_offset + 16)[0]
            config_size = struct.unpack_from("<I", data, type_offset + 20)[0]
            ui_mode = data[type_offset + 20 + 29] if config_size > 29 else 0
            if resource_type != TYPE_INTEGER or entry_count <= ENTRY_VOLUME_DIALOG_GRAVITY:
                continue
            if type_header < 20 + config_size or entries_start >= type_size:
                raise ValueError("Unexpected integer resource table layout")
            offsets_start = type_offset + type_header
            entry_offset = struct.unpack_from(
                "<I", data, offsets_start + ENTRY_VOLUME_DIALOG_GRAVITY * 4
            )[0]
            if entry_offset == 0xFFFFFFFF:
                continue
            entry_position = type_offset + entries_start + entry_offset
            if entry_position + 16 > type_offset + type_size:
                raise ValueError("Volume gravity entry is out of bounds")
            entry_header_size, entry_flags = struct.unpack_from("<HH", data, entry_position)
            if entry_flags & 0x01:  # complex resource; must not silently patch it
                raise ValueError("Volume gravity unexpectedly has a complex value")
            value_position = entry_position + entry_header_size
            value_size, _, value_type = struct.unpack_from("<HBB", data, value_position)
            value = struct.unpack_from("<I", data, value_position + 4)[0]
            if value_size < 8 or value_type != 0x10:  # TYPE_INT_DEC
                raise ValueError("Volume gravity is not an integer resource")
            is_television = (ui_mode & UI_MODE_TYPE_MASK) == UI_MODE_TYPE_TELEVISION
            if is_television:
                if value != GRAVITY_BOTTOM_CENTER:
                    raise ValueError(f"Unexpected television gravity: 0x{value:x}")
                struct.pack_into("<I", output, value_position + 4, GRAVITY_RIGHT_CENTER)
                found_tv += 1
            elif ui_mode == 0:
                if value == GRAVITY_RIGHT_CENTER:
                    found_default += 1
    if found_tv != 1 or found_default != 1:
        raise ValueError(f"Expected one TV and one default value; found {found_tv} and {found_default}")
    return bytes(output)


def signing_certificate(apk):
    result = subprocess.run(
        [str(SIGNER), "verify", "--print-certs", str(apk)],
        check=True, capture_output=True, text=True,
    )
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", result.stdout)
    if not match:
        raise ValueError(f"Could not read APK certificate: {apk}")
    return match.group(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("systemui_apk", type=Path, help="SystemUI.apk pulled from the HY300 Pro")
    parser.add_argument("--output", type=Path, help="Output APK path")
    args = parser.parse_args()
    source_apk = args.systemui_apk.resolve()
    if not source_apk.is_file():
        parser.error(f"APK does not exist: {source_apk}")
    if signing_certificate(source_apk) != EXPECTED_CERT:
        raise SystemExit("Unexpected SystemUI signing certificate; refusing to patch")

    output = (args.output or ROOT / "systemui-volume/build/SystemUI-volume-vertical.apk").resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix="hy300-systemui-volume-") as temp_dir:
        patched_unsigned = Path(temp_dir) / "SystemUI-volume-unsigned.apk"
        with ZipFile(source_apk, "r") as original, ZipFile(patched_unsigned, "w") as patched:
            names = set(original.namelist())
            if "resources.arsc" not in names or GENERIC_ROW not in names or TELEVISION_ROW not in names:
                raise SystemExit("This SystemUI does not have the expected Android 11 TV resources")
            vertical_row = original.read(GENERIC_ROW)
            for item in original.infolist():
                contents = original.read(item.filename)
                if item.filename == "resources.arsc":
                    contents = patch_television_gravity(contents)
                elif item.filename == TELEVISION_ROW:
                    contents = vertical_row
                patched.writestr(item, contents, compress_type=item.compress_type)

        aligned_unsigned = Path(temp_dir) / "SystemUI-volume-aligned.apk"
        subprocess.run(
            [str(ZIPALIGN), "-f", "4", str(patched_unsigned), str(aligned_unsigned)],
            check=True,
        )

        certificate = Path(temp_dir) / "platform.x509.pem"
        key = Path(temp_dir) / "platform.pk8"
        certificate.write_bytes(fetch(certificate.name))
        cert_digest = hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate.read_text())).hexdigest()
        if cert_digest != EXPECTED_CERT:
            raise SystemExit("Unexpected AOSP platform certificate; refusing to sign")
        key.write_bytes(fetch(key.name))
        key.chmod(0o600)
        subprocess.run(
            [str(SIGNER), "sign", "--key", str(key), "--cert", str(certificate),
             "--out", str(output), str(aligned_unsigned)],
            check=True,
        )

    output.chmod(0o600)
    subprocess.run([str(SIGNER), "verify", str(output)], check=True)
    if signing_certificate(output) != EXPECTED_CERT:
        raise SystemExit("Signed APK certificate verification failed")
    badging = subprocess.run(
        [str(AAPT), "dump", "badging", str(output)],
        check=True, capture_output=True, text=True,
    ).stdout
    if "package: name='com.android.systemui'" not in badging:
        raise SystemExit("Patched APK is not com.android.systemui")
    print(f"Created {output}")
    print("TV panel gravity: right | center_vertical (0x15)")
    print("TV volume row: vertical seek bar (90 degree rotation)")


if __name__ == "__main__":
    main()
