# Benchmarks iOS app

Open `BenchmarksIosApp.xcodeproj` in Xcode and select the shared `BenchmarksIosApp` scheme.
A Run Script builds the static `BenchmarksApp` framework with `embedAndSignAppleFrameworkForXcode`
before Swift compilation, as in DemoApp. A UIKit application delegate selects the launch path:
`averageFrame` and `fps` run without creating a window or Compose view controller;
UI benchmarks use a window with the Compose view controller.

## Parameters

In **Product → Scheme → Edit Scheme → Run → Arguments**, set three launch arguments:

1. `benchmarkCollectorUrl`, for example `http://127.0.0.1:8090/reports`.
2. Test name, for example `LazyGrid`.
3. Benchmark type: `averageFrame`, `fps`, `realFrames`, or `startup`.

The shared scheme uses `averageFrame`. The app runs the selected case and sends a report to the collector.
All three arguments are required. `AppDelegate.init` calls `initializeStartupTiming()` before launch
arguments and UI are processed. This captures the Kotlin entry mark and reads the process start time
for `startup` reports. Launch a new process for each cold startup measurement.
See the [main README](../README.md) for collector setup and network limits.

## Build settings

- iOS deployment target: 16.2, matching DemoApp.
- Supported architectures: ARM64 device and ARM64 simulator, matching `benchmarks-app`.
- For a physical device, select your development team under **Signing & Capabilities**.
- User Script Sandboxing is disabled so Gradle can build the framework.
- Framework search paths use the repository's default `out` layout. If using a custom Gradle `OUT_DIR`,
  override the Xcode `CMP_OUT_DIR` build setting to the same directory.

There is no separate Gradle module for this Xcode application; its Kotlin framework is built by
`:compose:mpp:newbenchmarks:benchmarks-app`.
