#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/dist/AirMouse.app"
if [ ! -d "$APP" ]; then
  echo "First build AirMouse with: bash scripts/build-all.sh"
  read -r -p "Press Return to close."
  exit 1
fi
open "$APP"
