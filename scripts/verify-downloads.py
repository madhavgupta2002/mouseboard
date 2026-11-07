#!/usr/bin/env python3
"""Verify committed download checksums and binary formats on any platform."""
import hashlib
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
folder = ROOT / "downloads"
expected = {"MouseBoard-Windows-x64.exe", "MouseBoard-macOS.dmg", "MouseBoard-Android.apk"}
entries = {}
for line in (folder / "SHA256SUMS").read_text().splitlines():
    digest, name = line.split("  ", 1)
    entries[name] = digest
assert entries.keys() == expected, "Download list is incomplete or has unexpected files"
for name, digest in entries.items():
    path = folder / name
    assert 0 < path.stat().st_size < 100 * 1024 * 1024, f"Invalid download size: {name}"
    assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, f"Checksum mismatch: {name}"
    print(f"PASS: {name} SHA-256")
exe = (folder / "MouseBoard-Windows-x64.exe").read_bytes()
offset = struct.unpack_from("<I", exe, 0x3c)[0]
assert exe[:2] == b"MZ" and exe[offset:offset+4] == b"PE\0\0"
assert struct.unpack_from("<H", exe, offset+4)[0] == 0x8664
print("PASS: Windows download is an x64 executable")
with (folder / "MouseBoard-macOS.dmg").open("rb") as dmg:
    dmg.seek(-512, 2)
    assert dmg.read(4) == b"koly"
print("PASS: Mac download is an Apple disk image")
with zipfile.ZipFile(folder / "MouseBoard-Android.apk") as apk:
    assert {"AndroidManifest.xml", "classes.dex", "resources.arsc"} <= set(apk.namelist())
    assert apk.testzip() is None
print("PASS: Android download is a readable APK")
if sys.platform == "darwin":
    with tempfile.TemporaryDirectory(prefix="mouseboard-verify-") as temporary:
        mount = Path(temporary) / "volume"
        mount.mkdir()
        subprocess.run(["hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint",
                        str(mount), str(folder / "MouseBoard-macOS.dmg")],
                       stdout=subprocess.DEVNULL, check=True)
        try:
            app = mount / "MouseBoard.app"
            subprocess.run(["codesign", "--verify", "--deep", "--strict", str(app)], check=True)
            architectures = subprocess.check_output(
                ["lipo", "-archs", str(app / "Contents/MacOS/MouseBoard")], text=True).split()
            assert set(architectures) == {"arm64", "x86_64"}
            bundled = app / "Contents/Resources/MouseBoard-Android.apk"
            assert bundled.read_bytes() == (folder / "MouseBoard-Android.apk").read_bytes()
            assert (mount / "Applications").is_symlink()
            assert (mount / "Applications").readlink() == Path("/Applications")
            print("PASS: Mounted DMG contains signed universal app, matching APK and Applications shortcut")
        finally:
            subprocess.run(["hdiutil", "detach", str(mount)], stdout=subprocess.DEVNULL, check=True)
