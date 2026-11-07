#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/dist/MouseBoard.app"
if [ ! -f "$ROOT/dist/MouseBoard-Android.apk" ]; then
  echo "Build the phone app first: bash scripts/build-android.sh" >&2
  exit 1
fi
swift build --package-path "$ROOT/apps/macos" -c release --arch arm64 --arch x86_64
BIN="$(swift build --package-path "$ROOT/apps/macos" -c release --arch arm64 --arch x86_64 --show-bin-path)"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN/MouseBoard" "$APP/Contents/MacOS/MouseBoard"
ARCHITECTURES=" $(lipo -archs "$APP/Contents/MacOS/MouseBoard") "
[[ "$ARCHITECTURES" == *" arm64 "* && "$ARCHITECTURES" == *" x86_64 "* ]]
cp "$ROOT/dist/MouseBoard-Android.apk" "$APP/Contents/Resources/MouseBoard-Android.apk"
cp "$ROOT/apps/macos/Info.plist" "$APP/Contents/Info.plist"
mkdir -p "$ROOT/dist/MouseBoard.iconset"
swift "$ROOT/scripts/create-icon.swift" "$ROOT/dist/MouseBoard.iconset"
iconutil -c icns "$ROOT/dist/MouseBoard.iconset" -o "$APP/Contents/Resources/MouseBoard.icns"
codesign --force --sign - --identifier dev.airmouse.mac "$APP"
echo "Built: $APP"
