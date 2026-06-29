#!/usr/bin/env bash
# Eval runner for Rokid Workout Tracker
# Runs lint + tests and reports pass/fail
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
WT="$ROOT/workout-tracker"
PASS=0
FAIL=0

echo "==> Eval: Running lint..."
if (cd "$WT" && ./gradlew :app:lint --no-daemon >/dev/null 2>&1); then
  echo "  ✓ lint passed"
  PASS=$((PASS+1))
else
  echo "  ✗ lint failed"
  FAIL=$((FAIL+1))
fi

echo "==> Eval: Running unit tests..."
if (cd "$WT" && ./gradlew :app:testDebugUnitTest --no-daemon >/dev/null 2>&1); then
  echo "  ✓ tests passed"
  PASS=$((PASS+1))
else
  echo "  ✗ tests failed"
  FAIL=$((FAIL+1))
fi

echo ""
echo "Eval result: ${PASS} passed, ${FAIL} failed"
[ "$FAIL" -eq 0 ]
