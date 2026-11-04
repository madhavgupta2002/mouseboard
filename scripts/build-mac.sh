#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/dist/AirMouse.app"
if [ ! -f "$ROOT/dist/AirMouse-Android.apk" ]; then
  echo "Build the phone app first: bash scripts/build-android.sh" >&2
  exit 1
fi
swift build --package-path "$ROOT/mac" -c release
BIN="$(swift build --package-path "$ROOT/mac" -c release --show-bin-path)"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN/AirMouse" "$APP/Contents/MacOS/AirMouse"
cp "$ROOT/dist/AirMouse-Android.apk" "$APP/Contents/Resources/AirMouse-Android.apk"
cp "$ROOT/mac/Info.plist" "$APP/Contents/Info.plist"
mkdir -p "$ROOT/dist/AirMouse.iconset"
swift "$ROOT/scripts/create-icon.swift" "$ROOT/dist/AirMouse.iconset"
iconutil -c icns "$ROOT/dist/AirMouse.iconset" -o "$APP/Contents/Resources/AirMouse.icns"
codesign --force --sign - --identifier dev.airmouse.mac "$APP"
echo "Built: $APP"
