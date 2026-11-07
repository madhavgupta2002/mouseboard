#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/build-mac.sh"
STAGING="$(mktemp -d "${TMPDIR:-/tmp}/mouseboard-dmg.XXXXXX")"
trap 'rm -rf "$STAGING"' EXIT
ditto "$ROOT/dist/MouseBoard.app" "$STAGING/MouseBoard.app"
ln -s /Applications "$STAGING/Applications"
cat > "$STAGING/Read Me.txt" <<'TEXT'
Install MouseBoard

1. Drag MouseBoard to Applications.
2. Open MouseBoard from Applications.
3. Pair your phone in Bluetooth settings and send the bundled Android APK.
4. Install and open MouseBoard on the phone. Tap Connect and choose your Mac.

Requires macOS 14 or newer. Works on Apple Silicon and Intel.
The Android installer is included; no build tools or desktop runtime required.

This personal project is not Apple-notarized. If macOS blocks the app, open
System Settings > Privacy & Security after trying to launch it, and choose
Open Anyway only if you trust the download.
TEXT
hdiutil create -volname MouseBoard -srcfolder "$STAGING" -format UDZO -ov \
  "$ROOT/dist/MouseBoard-macOS.dmg"
hdiutil verify "$ROOT/dist/MouseBoard-macOS.dmg"
echo "Built: $ROOT/dist/MouseBoard-macOS.dmg"
