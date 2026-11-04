#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
swift run --package-path "$ROOT/mac" AirMouseCoreChecks
mkdir -p "$ROOT/android/build/tests"
javac --release 8 -d "$ROOT/android/build/tests" \
  "$ROOT/android/app/src/main/java/dev/airmouse/MotionMath.java" "$ROOT/android/MotionMathTest.java" \
  "$ROOT/android/app/src/main/java/dev/airmouse/NetworkAddress.java" "$ROOT/android/NetworkAddressTest.java"
java -cp "$ROOT/android/build/tests" dev.airmouse.MotionMathTest
java -cp "$ROOT/android/build/tests" dev.airmouse.NetworkAddressTest
python3 "$ROOT/scripts/smoke-test.py"
