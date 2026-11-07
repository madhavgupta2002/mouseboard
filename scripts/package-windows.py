#!/usr/bin/env python3
"""Publish a self-contained Windows EXE on Windows or macOS with .NET 10."""
import argparse
import os
from pathlib import Path
import shutil
import struct
import subprocess

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--runtime", choices=["win-x64", "win-arm64"], default="win-x64")
args = parser.parse_args()
local = ROOT / ".tools/dotnet/dotnet"
dotnet = shutil.which("dotnet") or (str(local) if local.is_file() else None)
if not dotnet:
    parser.error("Install the .NET 10 SDK to build; end users only need the published EXE.")
apk = ROOT / "dist/MouseBoard-Android.apk"
if not apk.is_file():
    parser.error("Build Android first: bash scripts/build-android.sh")
output = ROOT / f"dist/MouseBoard-Windows-{args.runtime}"
if output.exists():
    shutil.rmtree(output)
env = dict(os.environ, DOTNET_CLI_TELEMETRY_OPTOUT="1", DOTNET_NOLOGO="1")
project = ROOT / "apps/windows/MouseBoard.Windows.csproj"
subprocess.run([dotnet, "publish", str(project), "-c", "Release", "-r", args.runtime,
                "--self-contained", "true", "-o", str(output), f"-p:InstallerApk={apk}"],
               cwd=project.parent, env=env, check=True)
exe = output / "MouseBoard.exe"
if not exe.is_file():
    raise SystemExit("Publish did not produce MouseBoard.exe")
unexpected = [p.name for p in output.iterdir() if p.name != exe.name]
if unexpected:
    raise SystemExit(f"Expected one EXE without companion files, found: {unexpected}")
data = exe.read_bytes()
offset = struct.unpack_from("<I", data, 0x3c)[0]
machine = struct.unpack_from("<H", data, offset + 4)[0]
assert data[:2] == b"MZ" and data[offset:offset+4] == b"PE\0\0"
assert machine == {"win-x64": 0x8664, "win-arm64": 0xaa64}[args.runtime]
assert len(data) < 100 * 1024 * 1024, "EXE exceeds GitHub's per-file limit"
assembly = project.parent / "bin/Release/net10.0-windows" / args.runtime / "MouseBoard.dll"
subprocess.run([dotnet, "run", "--project", str(project.parent / "tests/MouseBoard.Windows.Checks.csproj"),
                "-c", "Release", "--", str(assembly), str(apk)],
               cwd=project.parent, env=env, check=True)
destination = ROOT / "dist" / f"MouseBoard-Windows-{args.runtime.removeprefix('win-')}.exe"
shutil.copyfile(exe, destination)
print(f"Built: {destination} ({len(data)/1024/1024:.1f} MiB). One file; no .NET installation needed.")
