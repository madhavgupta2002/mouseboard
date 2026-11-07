#!/usr/bin/env python3
"""Copy verified release builds into the repository's download folder."""
import hashlib
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
DOWNLOADS = ROOT / "downloads"
NAMES = ("MouseBoard-Windows-x64.exe", "MouseBoard-macOS.dmg", "MouseBoard-Android.apk")
for name in NAMES:
    if not (ROOT / "dist" / name).is_file():
        raise SystemExit(f"Missing dist/{name}; build the platform package first.")
DOWNLOADS.mkdir(exist_ok=True)
lines = []
for name in NAMES:
    source = ROOT / "dist" / name
    if source.stat().st_size >= 100 * 1024 * 1024:
        raise SystemExit(f"{name} is too large to commit to GitHub.")
    shutil.copyfile(source, DOWNLOADS / name)
    lines.append(f"{hashlib.sha256(source.read_bytes()).hexdigest()}  {name}\n")
(DOWNLOADS / "SHA256SUMS").write_text("".join(lines))
subprocess.run(["python3", str(ROOT / "scripts/verify-downloads.py")], check=True)
