---
name: ios-instrumented-tests
description: This skill should be used when the user asks about "running iOS tests", "iOS instrumented tests", "how to run ios tests", "launch specific ios test", "add new ios instrumented test" and these tests are located in the "compose/ui/ui/src/uikitInstrumentedTest" directory.
version: 1.1.0
---

iOS Instrumented tests are designed to run XCTests written on Kotlin using the iOS Simulator as an environment.

# Key Test Source Locations
(from the repository root)
`compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/Configuration.kt` - location of the configuration file which determines which tests to run.
`compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/test` - Test environment and utilities to configure tests
`testutils/testutils-xctest/` - Contains a Kotlin wrapper that allows converting Kotlin tests to XCTests as well as an Objective-C project that provides API for low-level touches and other input gesture simulation.

# Running iOS Instrumented Tests

Perform steps exactly in the following order:

## Configure which tests should be started
The file compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/Configuration.kt defines two test suites:

- `testSuite()` runs all tests **except** the ones marked with `@FullKeyboardAccessTest`;
- `fullKeyboardAccessTestSuite()` runs **only** the tests marked with `@FullKeyboardAccessTest`
  (see "Simulator configuration" below — they need a simulator with Full Keyboard Access
  activated and are run with the `FULL_KEYBOARD_ACCESS_TESTS` option, see below).

Both suites are derived automatically from the test classes — no class list to maintain. Tests
marked with `@FullKeyboardAccessTest` must live in the `androidx.compose.ui.accessibility.fullkeyboardaccess`
package (`compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/accessibility/fullkeyboardaccess/`),
which is how the suites tell them apart at runtime.

To run only specific test classes or test cases instead of a whole suite, temporarily edit
`testSuite()` in Configuration.kt to call `setupXCTestSuite` with them explicitly — see the
examples in the KDoc of `testSuite()`.

## Running All iOS Tests

1. Get a list of all devices and select the most appropriate one. Prefer the latest iPhone with the latest iOS if nothing else said. Select and remember corresponding simulator id for the next step.
```
xcrun xctrace list devices
```

2. Navigate to the launcher repository root:

```bash
cd compose/ui/ui/src/uikitInstrumentedTest/launcher
```

3. Build and run tests
```
xcodebuild test -scheme Launcher -project Launcher.xcodeproj -destination 'platform=iOS Simulator,id=<Simulator ID>'
```

## Running Full Keyboard Access tests
The launcher runs the Full Keyboard Access suite instead of the general one when the
`FULL_KEYBOARD_ACCESS_TESTS` environment variable is set to `1`; with `xcodebuild test` it is
passed through the `TEST_RUNNER_` prefix:

```
TEST_RUNNER_FULL_KEYBOARD_ACCESS_TESTS=1 xcodebuild test -scheme Launcher -project Launcher.xcodeproj -destination 'platform=iOS Simulator,id=<Simulator ID>'
```

The simulator must have Full Keyboard Access activated first — see "Simulator configuration"
below; otherwise these tests fail with `No UIFocusSystem`.

# Creating iOS Instrumented Tests

The main class that creates environment to run tests is `compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/test/UIKitInstrumentedTest.kt`

When writing a new test or updating an existing one, prefer using the helpers from the following places:
- `compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/test/AccessibilityTestNode.kt`
- `compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/test/UIKitInstrumentedTest.kt`
- `compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/test`

In the test code avoid using the API from the "testutils-xctest" project directly.
If there is no corresponding API wrapper in the `compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/test`, add it.

## When a test is created or updated

After the test is created or updated, run it using the steps from the "Running iOS Instrumented Tests".

## Simulator configuration (tests may fail when it is not configured properly)

iOS instrumented tests may fail because the simulator is not configured properly, not
because of a product regression. The canonical configuration lives in CI:

- the **`Configure Simulator`** step in `.github/actions/setup-ios-instrumented-test-environment/action.yml`
  writes the general accessibility flags — follow it when setting a simulator up locally;
- the **`Enable Full Keyboard Access`** step in `.github/workflows/tests.yml` (iOS Instrumented
  job) activates Full Keyboard Access between the two test runs — follow it to run the
  `@FullKeyboardAccessTest` tests locally.

Most notably, tests using the UIKit focus system fail with
`No UIFocusSystem: enable Full Keyboard Access on the simulator...` when Full Keyboard Access
is not active on the simulator. FKA requires **both**:

1. The preference: `xcrun simctl spawn <UDID> defaults write com.apple.Accessibility FullKeyboardAccessEnabled -bool true`
2. A hardware keyboard attached to the simulator — writing the preference alone is not enough
   and the Settings toggle merely mirrors the preference. Simulator.app attaches its virtual
   hardware keyboard when it opens a **booted** device, which activates FKA live (no reboot
   needed).

The activated state only lives until the simulator is shut down: after a cold boot FKA has to
be activated again by opening the device in Simulator.app. If FKA tests suddenly start failing
with `No UIFocusSystem` after a simulator reboot, this is the reason.
