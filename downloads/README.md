# MouseBoard downloads

Version **0.6.0**. These files are ready to run; no development tools are needed.

| Platform | File |
| --- | --- |
| Windows 10/11, x64 | [Download the standalone EXE](https://github.com/madhavgupta2002/mouseboard/raw/refs/heads/main/downloads/MouseBoard-Windows-x64.exe) |
| macOS 14+, Apple Silicon and Intel | [Download the DMG](https://github.com/madhavgupta2002/mouseboard/raw/refs/heads/main/downloads/MouseBoard-macOS.dmg) |
| Android 9+ with Bluetooth HID support | [Download the APK](https://github.com/madhavgupta2002/mouseboard/raw/refs/heads/main/downloads/MouseBoard-Android.apk) |

## Windows

Double-click the EXE. There is nothing to extract or install, and .NET is
included. The app prepares its bundled Android APK in your local app-data
folder, ready for Bluetooth File Transfer.

The EXE has no commercial code-signing certificate. If Windows SmartScreen
shows “Windows protected your PC,” choose **More info → Run anyway** only if you
trust this download. Managed computers can disallow unsigned apps.

## Mac

Open the DMG and drag **MouseBoard** onto **Applications**. Open MouseBoard
from Applications. It works on both Intel and Apple Silicon and includes the
Android APK.

The app is locally signed but not Apple-notarized. If macOS blocks it, first
try opening it, then open **System Settings → Privacy & Security → Open Anyway**
only if you trust this download.

## Android

Open this page on the phone, download the APK and open it. Allow installation
from the browser/file manager when Android asks. Alternatively, send the APK
from the Windows or Mac app. Open MouseBoard, allow Nearby devices, pair the
computer and tap **Connect**. Install updates over the existing app.

MouseBoard uses Bluetooth directly. The desktop helper can be closed once the
phone is set up. The phone's Settings switch **Mac gesture shortcuts** should
be off when controlling Windows.

## File integrity

[`SHA256SUMS`](SHA256SUMS) contains the hashes of the three files. Developers can
run `python3 scripts/verify-downloads.py` from the repository root. Downloads
are built from source using the scripts described in
[development](../docs/development.md).
