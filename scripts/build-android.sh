#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/scripts/bootstrap-android.py"
SDK="$ROOT/.tools/android"
TOOLS="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
BUILD="$ROOT/apps/android/build"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$ROOT/dist"
rm -rf "$BUILD/classes" "$BUILD/dex"
mkdir -p "$BUILD/classes" "$BUILD/dex"
"$TOOLS/aapt2" compile --dir "$ROOT/apps/android/app/src/main/res" -o "$BUILD/resources.zip"
"$TOOLS/aapt2" link -o "$BUILD/base.apk" -I "$JAR" \
  --manifest "$ROOT/apps/android/app/src/main/AndroidManifest.xml" \
  --java "$BUILD/generated" --min-sdk-version 28 --target-sdk-version 35 \
  "$BUILD/resources.zip"
python3 - "$ROOT" "$BUILD" <<'PY'
import json, pathlib, sys
root, build = map(pathlib.Path, sys.argv[1:])
sources = sorted((root / "apps/android/app/src/main/java").rglob("*.java"))
sources += sorted((build / "generated").rglob("*.java"))
(build / "sources.txt").write_text("\n".join(json.dumps(str(path)) for path in sources) + "\n")
PY
javac --release 8 -classpath "$JAR" -d "$BUILD/classes" @"$BUILD/sources.txt"
jar cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$TOOLS/d8" --lib "$JAR" --min-api 28 --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && zip -q -u "$BUILD/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
KEY="$ROOT/.tools/airmouse-debug.keystore"
if [ ! -f "$KEY" ]; then
  keytool -genkeypair -keystore "$KEY" -storepass android -keypass android \
    -alias airmouse -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=MouseBoard Local Development" -noprompt
fi
"$TOOLS/apksigner" sign --ks "$KEY" --ks-key-alias airmouse \
  --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/dist/MouseBoard-Android.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify "$ROOT/dist/MouseBoard-Android.apk"
echo "Built: $ROOT/dist/MouseBoard-Android.apk"
