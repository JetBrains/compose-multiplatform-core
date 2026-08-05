#!/bin/bash

set -euo pipefail

# Edit these values directly when you want to change the target simulator or run count.
# Use `xcrun xctrace list devices` to find the simulator names and OS versions available locally.
platform="iOS Simulator"
os_version="26.5"
device_name="iPhone 17"
iterations="1"
# `run_until_failure` is applied only when iterations > 1.
run_until_failure="false"
# When true, xcodebuild output is mirrored into last-run-<n>.log next to this script.
log_to_file="false"

if [[ -z "$platform" || -z "$os_version" || -z "$device_name" ]]; then
  echo "Platform, OS, and device name must be non-empty." >&2
  exit 1
fi

if [[ ! "$iterations" =~ ^[1-9][0-9]*$ ]]; then
  echo "Iterations must be a positive integer, got: $iterations" >&2
  exit 1
fi

if [[ "$run_until_failure" != "true" && "$run_until_failure" != "false" ]]; then
  echo "run_until_failure must be true or false, got: $run_until_failure" >&2
  exit 1
fi

if [[ "$log_to_file" != "true" && "$log_to_file" != "false" ]]; then
  echo "log_to_file must be true or false, got: $log_to_file" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

destination="platform=${platform},OS=${os_version},name=${device_name}"

if [[ "$iterations" -eq 1 ]]; then
  log_file_pattern="last-run.log"
else
  log_file_pattern="last-run-<n>.log"
fi

if [[ "$log_to_file" == "true" ]]; then
  rm -f last-run.log last-run-[0-9]*.log
  export NSUnbufferedIO=YES
else
  log_file_pattern="disabled"
fi

log_pipe() {
  if [[ "$log_to_file" != "true" ]]; then
    cat
  elif [[ "$iterations" -eq 1 ]]; then
    tee "${script_dir}/last-run.log"
  else
    awk -v dir="$script_dir" '
      BEGIN { n = 1; file = dir "/last-run-1.log" }
      /Test Suite .All tests. started/ { if (started) { n++; file = dir "/last-run-" n ".log" } started = 1 }
      { print; print > file; fflush() }
    '
  fi
}

echo "Running iOS instrumented tests with:"
echo "  destination: ${destination}"
echo "  iterations: ${iterations}"
echo "  derivedDataPath: Xcode default"
echo "  log file: ${log_file_pattern}"

# The keyboard preference is picked up when a simulator boots, so shut them all down
# before forcing the detached-keyboard setup required by these instrumented tests.
xcrun simctl shutdown all
defaults write com.apple.iphonesimulator ConnectHardwareKeyboard -bool false

# Build once, then reuse the build products for the actual test execution.
xcodebuild \
  -project Launcher.xcodeproj \
  -scheme Launcher \
  -destination "$destination" \
  build-for-testing

test_args=(
  -collect-test-diagnostics on-failure
)

if [[ "$iterations" -gt 1 ]]; then
  test_args=(
    -test-iterations "$iterations"
    -test-repetition-relaunch-enabled YES
    "${test_args[@]}"
  )

  if [[ "$run_until_failure" == "true" ]]; then
    test_args=(
      -run-tests-until-failure
      "${test_args[@]}"
    )
  fi
fi

set +e
xcodebuild \
  -project Launcher.xcodeproj \
  -scheme Launcher \
  -destination "$destination" \
  test-without-building \
  "${test_args[@]}" 2>&1 | log_pipe
test_exit_code=${PIPESTATUS[0]}
set -e

exit "$test_exit_code"
