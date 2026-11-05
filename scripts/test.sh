#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/android/build/tests"
javac --release 8 -d "$ROOT/android/build/tests" \
  "$ROOT/android/app/src/main/java/dev/airmouse/MotionMath.java" "$ROOT/android/MotionMathTest.java" \
  "$ROOT/android/app/src/main/java/dev/airmouse/MouseReports.java" "$ROOT/android/MouseReportsTest.java" \
  "$ROOT/android/app/src/main/java/dev/airmouse/HidDescriptor.java" \
  "$ROOT/android/app/src/main/java/dev/airmouse/KeyboardReports.java" "$ROOT/android/KeyboardReportsTest.java" \
  "$ROOT/android/app/src/main/java/dev/airmouse/TextEdit.java" "$ROOT/android/MotionStabilityTest.java"
java -cp "$ROOT/android/build/tests" dev.airmouse.MotionMathTest
java -cp "$ROOT/android/build/tests" dev.airmouse.MotionStabilityTest
java -cp "$ROOT/android/build/tests" dev.airmouse.MouseReportsTest
java -cp "$ROOT/android/build/tests" dev.airmouse.KeyboardReportsTest
python3 "$ROOT/scripts/verify-bluetooth.py"
