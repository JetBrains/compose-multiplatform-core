# Compose benchmarks

A project with Compose Multiplatform benchmarks for measuring application startup and rendering
performance. This is a standalone Gradle build, separate from the core build.

Run all commands below from `compose/mpp/benchmarks`. The launcher
requires JDK 17 or newer. Apple targets require macOS with Xcode.

## Modules

- `benchmarks-app` — an application with Compose benchmark cases.
- `benchmarks-runtime` — runs measurements and sends results.
- `benchmarks-compose-init-1-12` / `benchmarks-compose-init-1-13` / `benchmarks-compose-init-current` — the app selects one and directly calls its `configureBenchmarkSkiko()`.
- `benchmarks-data` — shared report models and benchmark case IDs.
- `benchmarks-report` — a JVM library that generates a static HTML report from a session directory.
- `benchmarks-collector` — receives reports over HTTP and saves them as JSON files.
- `benchmarks-launcher` — a console application that builds and runs benchmarks sequentially.
- `benchmarks-ios-app` — an iOS host application for benchmarks; a separate Xcode project.

## Building the launcher

Build the launcher and copy the distribution:

```bash
./gradlew :benchmarks-launcher:installDist
mkdir -p launcher
cp -R benchmarks-launcher/build/install/benchmarks-launcher/. ./launcher/
```

See all available options:

```bash
./launcher/bin/benchmarks-launcher --help
```

## Running benchmarks

For each version pair, the launcher visits every selected target and, for JS/WasmJS, every selected
browser. For each configuration it builds the collector and the release application, starts the
collector, and runs tests sequentially. Each test run gets a separate UUID. Native targets use a
separate application process per run. Web targets use Selenium WebDriver: rendering runs reuse one
browser session and tab per configuration, navigating to a new URL for each run. After receiving a
report, the tab navigates to `about:blank` to stop the previous workload. All web rendering runs finish
before startup runs; the rendering browser is closed, and every startup run launches and closes a
fresh browser session. It then moves to the next version pair. Builds always run; Gradle can reuse
unchanged task outputs.

Compose and Kotlin versions are run and displayed in the order supplied, ignoring duplicates after
their first occurrence. Kotlin versions form the outer loop, Compose versions the inner loop.
The report uses the first supplied Compose version as the baseline for each Kotlin series and
compares the last supplied Compose version against it to highlight regressions or improvements.
Missing results do not shift the baseline or the comparison to another version.

The matrix continues after test errors, timeouts, or a failed configuration build. If any configuration
fails, the launcher returns a nonzero exit code. On completion or interruption, it stops the processes
it started and keeps collected results.

Main options:

- `--target` — required list of targets, separated by spaces or commas: `jvm`, `macos` (ARM64),
  `js`, `wasmJs`, or `ios` (physical ARM64 iOS device or ARM64 Simulator). `all` selects all targets.
  Duplicate entries are ignored.
- `--compose-version` — a list of published Compose versions and/or `local`; defaults to `local`
  using the current core checkout (`composeCorePath=../../..` in `gradle.properties`). Specify a
  published version explicitly to benchmark it instead.
- `--kotlin-version` — a list of published Kotlin and matching Compose Compiler versions for the
  benchmarks project; defaults to one catalog version. Local core sources use their own Kotlin version.
- `--tests` — test names separated by spaces or commas, or `all`; defaults to all tests.
- `--modes` — measurement modes; defaults to all four:
  - `startup` — startup time to the first frames;
  - `realFrames` — frame measurements in a real UI;
  - `averageFrame` — average offscreen rendering measurements;
  - `fps` — per-frame offscreen rendering measurements.
- `--repeat` — repetitions of each test/mode pair; default: `1`.
- `--timeout` — report timeout in seconds for each test run and environment collection, excluding
  build time; default: `300`.
- `--port` — collector TCP port (`1`–`65535`), which must be free; default: `8090`.
- `--web-port` — static HTTP server port for JS/WasmJS (`0`–`65535`); default: `0` selects a free port.
- `--output-dir` — the base directory for results; it can already exist. Defaults to
  `$OUT_DIR/benchmark-reports` when `OUT_DIR` is set, otherwise `<project>/out/benchmark-reports`.
  Each invocation creates a new timestamped subdirectory for the whole version matrix.
- `--project-dir` — standalone benchmarks project directory; detected from the working directory or
  launcher location by default.
- `--compose-core-path` — core checkout for `local` Compose; defaults to the configured
  `composeCorePath`, otherwise `../../..` relative to the benchmarks project.
- `--ios-target` — iOS simulator UUID, physical iOS device UDID/CoreDevice UUID, or unique name.
  Defaults to the first booted iOS simulator, otherwise boots the first available one. Ignored for other targets.
- `--apple-development-team` — Apple development team ID for physical iOS device signing; defaults to Xcode
  project settings. Ignored for simulator and non-iOS targets.

Only `--target`, `--tests`, and `--modes` accept `all`; use it alone, not alongside individual values.

Browser targets require `--browser` with a list of executable paths, commands on `PATH`, or macOS
`.app` bundles, separated by spaces or commas; `--browser` can also be repeated. Quote paths containing spaces. Duplicate entries are
ignored. Each selected browser runs on each selected web target; non-web targets ignore this list.
Chromium, Firefox, and Safari are detected from the executable name. ChromeDriver/GeckoDriver launch
the selected Chromium/Firefox executable with a temporary profile for each session. Rendering runs
share the profile and its caches; there is no separate preload run. JS/Wasm and other static resources
are cacheable, while HTML is loaded on every navigation. Every startup run gets a fresh profile in
Chromium/Firefox. Safari uses an automation session managed by `safaridriver`; its caches are not
explicitly cleared. Safari must be closed before the series, and the launcher also quits the Safari
application after ending a session to ensure startup runs restart the browser.

Selenium Manager resolves ChromeDriver/GeckoDriver automatically, downloading them when necessary.
The first run may require network access; cache `~/.cache/selenium` on CI. Drivers can also be
provisioned explicitly using Selenium's `SE_CHROMEDRIVER` / `SE_GECKODRIVER` environment variables.
Safari uses the system `safaridriver` (or the Technology Preview driver for Safari Technology Preview).
Enable **Allow remote automation** in Safari's developer settings before running Safari benchmarks;
the launcher does not change this setting. Browser runs require a graphical desktop/session, as before.
WebDriver does not wait for the page's `load` event; the collector report determines completion.
After an error or timeout, the current run is failed without a retry, and the next run starts a new
browser session.

For iOS, the launcher uses the first booted iOS simulator, or boots the first available one.
Physical iOS devices are never selected automatically. Use `--ios-target ID_OR_NAME` to select
an available simulator by UUID or unique name, or a physical iOS device by UDID, CoreDevice UUID,
or unique name. An explicit selection must match exactly one target across both types;
missing or ambiguous matches fail without fallback. If discovery of one target type fails,
only an explicit ID can select a target of the other type; names require successful discovery of both.

Physical iOS devices require Xcode 15+, pairing, Developer Mode, and an unlocked screen. Pass
`--apple-development-team TEAM_ID` for signing without saving the team in the Xcode project.
The collector address is detected automatically; the iOS device must be able to reach the Mac over
the local network. Allow local-network access on iOS device and incoming collector connections on Mac.

### Example: JVM

Run two tests in startup and average rendering modes, with three repetitions:

```bash
./launcher/bin/benchmarks-launcher --target jvm \
  --compose-version 1.13.0-beta02 --kotlin-version 2.4.21 \
  --tests LazyGrid TextLayout --modes startup averageFrame --repeat 3
```

### Example: version matrix

Compare two Kotlin versions with published and local Compose (four configurations):

```bash
./launcher/bin/benchmarks-launcher --target jvm \
  --kotlin-version 2.4.20 2.4.21 --compose-version 1.13.0-beta02,local \
  --tests LazyGrid --modes startup --output-dir ./results
```

### Example: Firefox

Measure offscreen rendering in a WasmJS application:

```bash
./launcher/bin/benchmarks-launcher --target wasmJs \
  --browser "/Applications/Firefox.app" \
  --tests LazyGrid --modes averageFrame
```

### Example: local Compose

Build core sources and use Kotlin 2.4.20 for the benchmark project:

```bash
./launcher/bin/benchmarks-launcher --target jvm \
  --compose-version local --kotlin-version 2.4.20 \
  --tests LazyGrid --modes startup --output-dir ./results
```

## Compose and Skiko compatibility

`benchmarks-runtime` owns the scene and exposes configuration hooks without depending on any init
module. All init modules provide the same `org.jetbrains.compose.benchmarks.skiko.configureBenchmarkSkiko()` function.
The app calls it at startup to configure scene initialization and JVM/Apple graphics context providers.
Surface flushing is a `GraphicsContext` method implemented with the matching Skiko API in each init
module. No reflection or service loading is needed.

The app declares `benchmarks-compose-init-current` in `commonMain`. For published Compose versions,
Gradle substitutes `benchmarks-compose-init-1-12` for `1.12.x` and `benchmarks-compose-init-1-13` for
`1.13.0`–`1.13.9` (including prereleases) on all targets. Compose `1.13.10` and later use `current`.
The version comes from `benchmarkComposeVersion` or the catalog default. Local core builds always
keep `current`, regardless of the catalog version. Other versions also keep `current`.
Skiko and its native libraries come from Compose and are not overridden.

The `1-13` module preserves initialization for the Skiko APIs shipped with Compose `1.13.0`–`1.13.9`;
`current` uses the new Ganesh extension APIs. Both support Metal/OpenGL/DirectX offscreen contexts on JVM.
Legacy initialization for Compose `1.12.2` uses DirectX on Windows and null-rendering on macOS/Linux,
as in the original multiplatform benchmarks, and leaves the scene initialization hook as a no-op.
Offscreen GPU results from different backends are not directly comparable. Other old Compose versions
are not supported.

## Launcher results

By default, results go under `out/benchmark-reports`. `--output-dir` replaces this base directory.
Each invocation creates a UTC timestamp directory in `yyyy-MM-dd-HH-mm-ss-SSS` format, then groups
results by versions and target:

```text
<date>-<time>/
    index.html             # Interactive charts for this session
    session.json           # Original launcher arguments
    kotlin(2.4.21)-cmp(1.13.0-beta02)-reports/
        jvm/
            reports/       # JSON reports from all runs
            logs/          # Build, collector, and per-test logs
            summary.json   # Versions, build metadata, environment reference, and benchmark runs
```

## How to add a new benchmark case

The steps below use `MyBenchmark` as the case name. File paths are relative to
`compose/mpp/benchmarks`; run the commands from that directory.

### 1. Implement the Compose UI case

Add a package and Kotlin file under
`benchmarks-app/src/commonMain/kotlin/org/jetbrains/compose/benchmarks/app/benchmarks`, for example
`mybenchmark/MyBenchmark.kt`, with a `@Composable fun MyBenchmark()` entry point.
Use an existing case such as `canvasdrawing/CanvasDrawing.kt` or `lazygrid/LazyGrid.kt` as a reference.

Keep the case multiplatform and self-contained: avoid platform-specific APIs, network requests, and
manual interaction. For frame measurements, make the workload update automatically (for example,
with an animation or scrolling in `LaunchedEffect`) so it continues producing frames throughout
warmup and measurement. Keep input data and random seeds reproducible. The measurement runtime
already handles warmup, frame counts, timing, and report submission; do not implement these in the
UI case.

### 2. Add the case ID to `BenchmarkCase`

Add an entry to the enum in
`benchmarks-data/src/commonMain/kotlin/org/jetbrains/compose/benchmarks/data/BenchmarkCase.kt`:

```kotlin
MyBenchmark("MyBenchmark"),
```

The explicit string ID is used in launch arguments, JSON reports, and the HTML report. IDs must be
unique and are case-sensitive. Keep existing IDs stable so results remain comparable across runs.

The launcher derives its case list from `BenchmarkCase.entries`: the new case automatically becomes
available to `--tests`, is included in `--tests all`, and appears in help and validation. Do not add
a separate registration in the launcher.

### 3. Map the case to its Compose UI in `benchmarkContent`

In `benchmarks-app/src/commonMain/kotlin/org/jetbrains/compose/benchmarks/app/ArgumentsParser.kt`,
import the composable:

```kotlin
import org.jetbrains.compose.benchmarks.app.benchmarks.mybenchmark.MyBenchmark
```

Add a branch to the existing `when` in `benchmarkContent`:

```kotlin
BenchmarkCase.MyBenchmark -> { { MyBenchmark() } }
```

The outer branch returns a composable lambda; it must not call the composable directly. Keep the
`when` exhaustive, without an `else` branch: adding an enum entry without a matching UI branch must
cause a compilation error.

No additional registration is needed in the collector or HTML report generator: they use the case
name from the report. Adding a UI case does not require a new measurement mode or report format.

### 4. Rebuild and refresh the launcher distribution

The copied launcher is not updated automatically when its source changes:

```bash
./gradlew :benchmarks-launcher:installDist --console=plain
mkdir -p launcher
cp -R benchmarks-launcher/build/install/benchmarks-launcher/. ./launcher/
./launcher/bin/benchmarks-launcher --help
```

Check that `MyBenchmark` appears among the available cases. If the launcher rejects it as an
unknown `--tests` value, check the registration and make sure you are using the rebuilt distribution.

### 5. Run the new case through the launcher

Start with a single target and mode, using explicit versions for a reproducible run:

```bash
./launcher/bin/benchmarks-launcher --target jvm \
  --kotlin-version 2.4.21 --compose-version 1.13.0-beta02 \
  --tests MyBenchmark --modes realFrames --repeat 1 \
  --output-dir ./out/new-case-results
```

The launcher builds the application containing the new case, starts the collector, runs the case,
and generates an HTML report. There is no need to build the application or start the collector
separately. Use `--compose-version local` instead to investigate changes in a core checkout.

After the first successful run, check the other relevant modes (`startup`, `averageFrame`, and
`fps`) and platforms. For iOS, use `--target ios --ios-target <simulator-uuid>` on macOS; for web,
select `--target js` or `--target wasmJs` and add `--browser`. Offscreen modes (`averageFrame` and
`fps`) must work without a platform window or user interaction.

### 6. Check the HTML report and diagnose failures

The launcher prints `HTML report: <path>` when report generation finishes. Open that `index.html`
in a browser, for example on macOS:

```bash
open "./out/new-case-results/<session-timestamp>/index.html"
```
