# Benchmarks iOS app

Open `BenchmarksIosApp.xcodeproj` in Xcode and select the shared `BenchmarksIosApp` scheme.
The project follows the DemoApp setup: SwiftUI hosts the Compose view controller, and a Run Script
builds the static `BenchmarksApp` framework with `embedAndSignAppleFrameworkForXcode` before Swift compilation.

## Parameters

In **Product → Scheme → Edit Scheme → Run → Arguments**, set two launch arguments:

1. `bechmarkCollectorUrl`, for example `http://127.0.0.1:8090/reports`.
2. Test name, for example `LazyGrid`.

The shared scheme includes these example values. Both are displayed by the Compose application;
no benchmarks or network requests are performed. When launched without arguments, the values are empty.

## Build settings

- iOS deployment target: 16.2, matching DemoApp.
- Supported architectures: ARM64 device and ARM64 simulator, matching `benchmarks-app`.
- For a physical device, select your development team under **Signing & Capabilities**.
- User Script Sandboxing is disabled so Gradle can build the framework.
- Framework search paths use the repository's default `out` layout. If using a custom Gradle `OUT_DIR`,
  override the Xcode `CMP_OUT_DIR` build setting to the same directory.

There is no separate Gradle module for this Xcode application; its Kotlin framework is built by
`:compose:mpp:newbenchmarks:benchmarks-app`.
