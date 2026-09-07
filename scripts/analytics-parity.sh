#!/usr/bin/env bash
# P0-1 acceptance test: the iOS and Android analytics constants files must name
# exactly the same events, property keys and values, in the same order. Compares
# the quoted snake_case strings in each file, so only language syntax may differ.
#
#   scripts/analytics-parity.sh
#
# Exit 0 when they match, 1 with the diff (iOS "<", Android ">") when they don't.
set -euo pipefail
cd "$(dirname "$0")/.."

ios=Farmsy/Core/AnalyticsEvent.swift
android=android/app/src/main/java/app/farmsy/android/core/AnalyticsEvent.kt

extract() { grep -oE '"[a-z][a-z0-9_]*"' "$1"; }

if out=$(diff <(extract "$ios") <(extract "$android")); then
  echo "analytics parity OK: $(extract "$ios" | wc -l | tr -d ' ') names identical on iOS and Android"
else
  echo "analytics parity FAILED — iOS (<) vs Android (>):"
  echo "$out"
  exit 1
fi
