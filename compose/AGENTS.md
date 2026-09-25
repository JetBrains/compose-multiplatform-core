# Project: Compose Multiplatform Core

This directory contains the libraries that make up Compose Multiplatform Core.

## Project Structure Map
- **Runtime (`compose:runtime`):** The core engine for state management and the composition tree.
- **UI (`compose:ui`):** Orchestration layer for layout, input, graphics, and text primitives.
- **Foundation (`compose:foundation`):** Design-system-agnostic building blocks (e.g., `LazyColumn`, gestures).
- **Material/Material3 (`compose:material`, `compose:material3`):** Design-system-specific components implementing Material Design.

## General Instructions
- **Changes Verification:** 
  - Running the tests every time might take too long. Compile the tests before running. Choose the relevant task: `compileTestKotlinIosArm64`, `compileTestDevelopmentExecutableKotlinJs`, `compileTestDevelopmentExecutableKotlinWasmJs`, `desktopTestClasses`
- When to run the tests: when working on the tests, or fixing the implementation, or when asked explicitly.
- Which tests to run: for platform-specific changes run only platform tests. Otherwise, run the tests for all affected platforms.


## Testing
- We do not add tests in the `commonTest` folder. When it's possible, we add multiplatform tests to `skikoTest`.  Platform-specific tests should be added in the corresponding folder: `webTest`, `desktopTest`, `jvmTest`, `iosTest`, `iosInstrumentedTest`.
- When applicable, use platform-specific gradle tasks to run the tests: `desktopTest`,  `iosSimulatorArm64Test`, `wasmJsBrowserTest`, `jsBrowserTest`. Also clean the tests results before running. Example: `./gradlew :compose:ui:ui:cleanAllTests :compose:ui:ui:deskopTest --no-build-cache | tail -n 10`. Allow a reasonable timeout (at least 5 minutes).
