#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/dist/MouseBoard.app"
if [ ! -d "$APP" ]; then
  echo "First build MouseBoard with: bash scripts/build-all.sh"
  read -r -p "Press Return to close."
  exit 1
fi
open "$APP"
