#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BENCHMARKS_DIR="$ROOT_DIR/compose/mpp/benchmarks"
BUILD_LOG="$(mktemp "${TMPDIR:-/tmp}/run-benchmarks.XXXXXX")"
trap 'rm -f "$BUILD_LOG"' EXIT

if "$BENCHMARKS_DIR/gradlew" -p "$BENCHMARKS_DIR" :benchmarks-launcher:installDist --console=plain >"$BUILD_LOG" 2>&1; then
    rm -f "$BUILD_LOG"
    trap - EXIT
else
    status=$?
    cat "$BUILD_LOG" >&2
    exit "$status"
fi

if [ "$#" -eq 0 ]; then
    set -- --help
fi

LAUNCHER="$BENCHMARKS_DIR/benchmarks-launcher/build/install/benchmarks-launcher/bin/benchmarks-launcher"
printf '%q ' "$LAUNCHER" "$@"
printf '\n'
exec "$LAUNCHER" "$@"
