# Project: Compose Multiplatform Core

This directory contains the Compose runtime, UI, Foundation, and Material
libraries used by Compose Multiplatform. This repository is a fork of AndroidX;
upstream changes are merged regularly, while this file is maintained for the
Compose Multiplatform team's workflow.

## Scope

- The team develops and verifies the common, Desktop, iOS/Darwin, and Web
  (JavaScript and Wasm) targets.
- Do not build or test Android targets as part of normal verification. Do not
  run Android compilation, `connectedAndroidTest`, or `androidDeviceTest`
  tasks unless the user explicitly asks for Android work.
- Preserve Android behavior when changing shared code, but rely on upstream
  AndroidX and its CI for Android-specific implementation and verification.
- Work from the repository root (`compose-multiplatform-core`) and use the
  Gradle wrapper in that directory.

## Project Map

- `compose/runtime`: state, snapshots, and the composition engine.
- `compose/ui`: layout, input, graphics, text, accessibility, and platform
  integration.
- `compose/foundation`: design-system-independent components and interaction
  primitives.
- `compose/material` and `compose/material3`: Material components.
- `compose/desktop`: Desktop-specific APIs and examples.
- `compose/mpp`: multiplatform demos and aggregate build/test tasks.

## Source Sets and Platform Code

- Put platform-independent production code in `commonMain` and tests in
  `commonTest` whenever practical.
- Use the narrowest existing shared source set for platform behavior. Common
  source sets in this repository include `skikoMain`, `nonAndroidMain`,
  `webMain`, `nativeMain`, and `darwinMain`.
- Use `desktopMain`, `iosMain`, `jsMain`, `wasmJsMain`, or another leaf source
  set only when the behavior truly differs for that target.
- Follow the module's existing source-set hierarchy; inspect its Gradle build
  file before adding a new source set or `expect`/`actual` declaration.
- Avoid changing `androidMain`, `androidTest`, `androidDeviceTest`, or other
  Android-only sources unless the requested change explicitly requires it.
- When shared code originated upstream, keep changes easy to reconcile with
  future AndroidX merges. Prefer a targeted platform implementation over
  duplicating or broadly rewriting upstream code.

## Implementation Standards

- Match the style and architecture of the surrounding module.
- Prefer `Modifier.Node` for modifiers; avoid `composed {}` unless required by
  an established compatibility constraint.
- Keep hot paths allocation-conscious and avoid unnecessary work during
  composition, measurement, drawing, and pointer processing.
- Add public API only when it is needed. Use experimental opt-ins for genuinely
  unstable API, not merely because an API is new.
- Do not add or upgrade a dependency without checking existing repository usage
  and confirming the change with the user.
- Use `git mv` for file moves. Do not create commits unless explicitly asked.

## Formatting and API Compatibility

- Format every modified Kotlin file before considering the change complete:

  ```bash
  ./gradlew :ktCheckFile --format --file <path> [--file <path> ...]
  ```

- Compose Multiplatform API baselines are `*.api` files. For a public API
  change, run:

  ```bash
  ./gradlew jbApiCheck
  ./gradlew jbApiDump
  ```

  Inspect all generated baseline changes. Additions should be intentional;
  removals or signature changes are potentially binary incompatible and must
  be resolved rather than accepted blindly. Prefer a narrower module API task
  when the appropriate task is known.

## Testing and Verification

- For a bug fix, first add the smallest regression test that reproduces the
  problem when practical, then implement the fix.
- Put tests in the broadest applicable non-Android test source set: usually
  `commonTest`, `skikoTest`, `desktopTest`, `webTest`, `jsTest`, `wasmJsTest`,
  `nativeTest`, `darwinTest`, or `iosTest`.
- Run the narrowest relevant module tasks first. Use aggregate tasks only when
  the change is broad:

  ```bash
  ./gradlew desktopTest
  ./gradlew :mpp:testWeb
  ./gradlew :mpp:testIos
  ```

- Choose verification according to the affected source sets. A common or Skiko
  change generally needs coverage on more than one owned platform; a leaf
  platform change needs that platform's compile/test task.
- Discover module-specific tasks with `./gradlew <project>:tasks --all` instead
  of substituting an Android compile task.
- iOS instrumented tests under `compose/ui/ui/src/uikitInstrumentedTest` use
  the Xcode launcher and repository run configuration described in
  `MULTIPLATFORM.md`; they are not Android instrumented tests.
- For visual changes, use the screenshot or rendering-test infrastructure
  already used by the affected non-Android source set. Do not introduce an
  Android screenshot test as the sole verification for a Multiplatform change.
- If host limitations prevent a relevant Apple or browser test, run all
  available checks and report exactly what remains unverified.

## Documentation and Samples

- Document platform differences and unsupported behavior explicitly in KDoc.
- New public user-facing APIs should include a focused sample when the module
  has a corresponding samples project. Follow that project's existing source
  set and link the sample with `@sample`.
- Update an existing demo or sample when it is the clearest way to exercise a
  platform integration, but do not add unrelated Android sample work.
