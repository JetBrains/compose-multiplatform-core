# Repository guidance

This is a fork of androidx monorepo dedicated for Compose Multiplatform (CMP) work.
The purpose of the fork is to provide missing Kotlin targets for modules registered in `JetBrainsPublication.kt` (mainly Compose-related).
Generally we add support for iOS, Web and Desktop. and we do not validate, build and test Android. The full list of which targets are not maintained is defined in the associated `build-fork.gradle`, in the `redirect` block.

The primary focus of Compose Multiplatform is the `./compose` directory.
Most often, we work in `./compose/ui/ui` and `./compose/foundation/foundation`.
Also, we publish klibs for some other libraries: `./navigation`, `./navigation3`.

The repo contains both the original AOSP code and additional fork-only code.

The repo supports two modes: 
- The original AOSP mode (`aospComposeProject.sh`), with the original set of modules, and fork mode, with a much smaller set of modules and its own build infrastructure. 
- Fork mode is the primary mode and is used by default; AOSP mode is rarely needed.

- Common code, used by both modes:
  - `commonMain`, `commonTest`, and other source sets defined both in `build.gradle` and `build-fork.gradle` files
  - Received from the upstream AOSP repo
- Fork-only code:
  - Independent build infrastructure: `settings-fork.gradle`, `buildSrc-fork`, `build-fork.gradle`, and `./gradle/libs-fork.versions.toml`
  - if modules exists only in `settings-fork.gradle`, but not in `settings.gradle`, it may just contain `build.gradle` without `build-fork.gradle`
  - iOS, desktop, JS, and WasmJS source sets, their combinations, and their corresponding test source sets
- AOSP-only code:
  - Independent build infrastructure: `settings.gradle`, `buildSrc`, `build.gradle`, and `./gradle/libs.versions.toml`
  - `androidMain` and its corresponding test source sets
  - `*StubsMain` source sets - created in the upstream AOSP repo to ensure `commonMain` compilation on the all Kotlin targets
  - iOS, desktop, JS, and WasmJS source sets for libraries and targets developed in AOSP

## Instruction files

A session may not automatically load instructions in descendant directories.
Read these files when working in the corresponding project parts:
- `./compose/AGENTS.md`
- `./compose/material3/material3/AGENTS.md`
- `./ai-skills/ios-instrumented-tests-compose/SKILL.md` for running iOS instrumented tests
- `./ai-skills/update-kotlin-version/SKILL.md` for updating the Kotlin version

When delegating, refer the subagent to those files too.
Note: Since this a fork, new instruction files might get merged from the upstream. Ignore AOSP-specific instructions / gradle tasks / checks / verifications (majority of them are about Android)

## General instructions

### Git
- Do not create git commits unless explicitly requested.

### Gradle
- Running Gradle tasks:
  - The output is usually very large and most of it is irrelevant. Unless it's necessary, avoid reading a full output by using `grep`, `tail`, etc.
  - Also, use `--console=plain`
  - When investigating a build failure, save the build output to a temporary file and then use `grep`.

### Introducing new changes
- In this fork we avoid introducing code changes in the common code (see above). Before changing any code in them, notify and request an approval when such a change is necessary.
- API: Unless a feature is intended for GA, avoid introduction of public API changes.
  - Depending on the module's published targets, fork-mode API dumps live in `api/desktop/<name>.api`,
    `api/android/<name>.api`, and `api/<name>.klib.api`. Run `./gradlew jbApiDump` to regenerate them.
    The `API` workflow runs `./gradlew jbApiCheck` on every pull request.
  - The `updateApi` task mentioned in `navigation/AGENTS.md` and `navigation3/AGENTS.md` and the Metalava files
    `current.txt` and `restricted_current.txt` belong to AOSP mode.
- Refactoring, syntactic sugar and code re-formatting: avoid "sugaring" the existing code unless explicitly asked; avoid renaming or moving code unless it's necessary for the task implementation or unless it's explicitly requested; same for code formatting.

### Changes Verification
- Running the tests every time might take too long. Compile the tests before running. Choose the relevant task: `compileTestKotlinIosArm64`, `compileTestDevelopmentExecutableKotlinJs`, `compileTestDevelopmentExecutableKotlinWasmJs`, `desktopTestClasses`
  - Run iOS and macOS compile checks on macOS. On other hosts, Gradle skips disabled Apple targets, and
    `kotlin.native.ignoreDisabledTargets=true` in `gradle.properties` suppresses the warning.
- When to run the tests: when working on the tests, or fixing the implementation, or when asked explicitly.
- Which tests to run: for platform-specific changes run only platform tests. Otherwise, run the tests for all affected platforms.

### Testing
- We do not add tests in the `commonTest` folder. When possible, add multiplatform tests to `skikoTest`. Add
  platform-specific tests to the corresponding source set, such as `webTest`, `desktopTest`, `jvmTest`, or `iosTest`.
  The `iosInstrumentedTest` source set uses `src/uikitInstrumentedTest/kotlin`.
- Use the applicable platform-specific Gradle task (`desktopTest`, `iosSimulatorArm64Test`, `wasmJsBrowserTest`,
  or `jsBrowserTest`) and clean the test results before running. For example, run
  `./gradlew :compose:ui:ui:cleanAllTests :compose:ui:ui:desktopTest --no-build-cache --console=plain`.
  Allow a reasonable timeout (at least 5 minutes).
- While iterating, put `--tests "<fully qualified class>"` immediately after `desktopTest`. Run the whole module's
  suite at the end. `desktopTest` includes `skikoTest` in `ui` and `foundation`, and `nonAndroidTest` in `ui-text`
  and `ui-skiko`.
- Results go to `out/compose-multiplatform-core/<module dir>/build/test-results/<task>/TEST-*.xml`. `$OUT_DIR`
  replaces `out` when set. Gradle logs each test's outcome (`PASSED`, `FAILED`, or `SKIPPED`) and standard output.
  Use the XML or search the full log for `FAILED` to identify failures above the build summary.
