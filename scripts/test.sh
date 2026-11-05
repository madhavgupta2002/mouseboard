#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-all}"
if [[ "$MODE" != "all" && "$MODE" != "--unit-only" ]]; then
  echo "Usage: bash scripts/test.sh [--unit-only]" >&2
  exit 2
fi
mkdir -p "$ROOT/apps/android/build/tests"
javac --release 8 -d "$ROOT/apps/android/build/tests" \
  "$ROOT/apps/android/app/src/main/java/dev/airmouse/MotionMath.java" "$ROOT/tests/android/MotionMathTest.java" \
  "$ROOT/apps/android/app/src/main/java/dev/airmouse/MouseReports.java" "$ROOT/tests/android/MouseReportsTest.java" \
  "$ROOT/apps/android/app/src/main/java/dev/airmouse/HidDescriptor.java" \
  "$ROOT/apps/android/app/src/main/java/dev/airmouse/KeyboardReports.java" "$ROOT/tests/android/KeyboardReportsTest.java" \
  "$ROOT/apps/android/app/src/main/java/dev/airmouse/TextEdit.java" "$ROOT/tests/android/MotionStabilityTest.java"
java -cp "$ROOT/apps/android/build/tests" dev.airmouse.MotionMathTest
java -cp "$ROOT/apps/android/build/tests" dev.airmouse.MotionStabilityTest
java -cp "$ROOT/apps/android/build/tests" dev.airmouse.MouseReportsTest \
  "$ROOT/apps/android/build/tests/hid-descriptor.bin"
java -cp "$ROOT/apps/android/build/tests" dev.airmouse.KeyboardReportsTest
if [[ "$MODE" != "--unit-only" ]]; then
  mkdir -p "$ROOT/apps/macos/.build"
  swiftc "$ROOT/apps/macos/Sources/MouseBoard/HidInputProfile.swift" \
    "$ROOT/tests/macos/main.swift" -o "$ROOT/apps/macos/.build/hid-profile-checks"
  "$ROOT/apps/macos/.build/hid-profile-checks" \
    "$ROOT/apps/android/build/tests/hid-descriptor.bin"
  python3 "$ROOT/scripts/verify-bluetooth.py"
fi
