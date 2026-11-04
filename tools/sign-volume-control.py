#!/usr/bin/env python3
"""Sign the HY300 Pro volume replacement with its verified platform test key."""
import base64
import hashlib
import os
from pathlib import Path
import ssl
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
SDK = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
SIGNER = SDK / "build-tools/35.0.0/apksigner"
UNSIGNED = ROOT / "volume-control/build/outputs/apk/release/volume-control-release-unsigned.apk"
OUTPUT = UNSIGNED.with_name("volume-control-hy300pro.apk")
SOURCE = "https://android.googlesource.com/platform/build/+/refs/tags/android-11.0.0_r1/target/product/security/"
EXPECTED = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"


def fetch(name):
    with urllib.request.urlopen(SOURCE + name + "?format=TEXT", timeout=30) as response:
        return base64.b64decode(response.read())


if not UNSIGNED.is_file():
    raise SystemExit("Build :volume-control:assembleRelease first")
os.umask(0o077)
with tempfile.TemporaryDirectory(prefix="hy300-volume-sign-") as directory:
    cert = Path(directory) / "platform.x509.pem"
    key = Path(directory) / "platform.pk8"
    cert.write_bytes(fetch(cert.name))
    digest = hashlib.sha256(ssl.PEM_cert_to_DER_cert(cert.read_text())).hexdigest()
    if digest != EXPECTED:
        raise SystemExit("Unexpected firmware certificate; refusing to sign")
    key.write_bytes(fetch(key.name))
    key.chmod(0o600)
    subprocess.run([str(SIGNER), "sign", "--key", str(key), "--cert", str(cert),
                    "--out", str(OUTPUT), str(UNSIGNED)], check=True)
    OUTPUT.chmod(0o600)
    subprocess.run([str(SIGNER), "verify", str(OUTPUT)], check=True)
print("Signed volume replacement:", OUTPUT.relative_to(ROOT))
