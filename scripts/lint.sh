#!/usr/bin/env bash
# Lint wrapper for Rokid Workout Tracker
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
WT="$ROOT/workout-tracker"

echo "==> Running Android lint..."
cd "$WT"
./gradlew :app:lint --no-daemon

REPORT="$WT/app/build/reports/lint-results-debug.html"
if [ -f "$REPORT" ]; then
  echo "==> Report: $REPORT"
fi
