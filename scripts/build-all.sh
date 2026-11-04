#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/build-android.sh"
bash "$ROOT/scripts/build-mac.sh"
