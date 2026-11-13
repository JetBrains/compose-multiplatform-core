# Compose benchmarks

A project with Compose Multiplatform benchmarks for measuring application startup and rendering
performance. This is a standalone Gradle build, separate from the core build.

Run all commands below from `compose/mpp/benchmarks`. The launcher runs on macOS and Linux
and requires JDK 17 or newer. Apple targets require macOS with Xcode.

## Modules

- `benchmarks-app` — an application with Compose benchmark cases.
- `benchmarks-runtime` — runs measurements and sends results.
- `benchmarks-skiko-init-legacy` / `benchmarks-skiko-init-current` — the app selects one and directly calls its `configureBenchmarkSkiko()`.
- `benchmarks-data` — shared report models.
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
collector, and runs tests sequentially. Each test run gets a separate process and UUID.
After receiving a report, it closes the application and starts the next test. It then moves to the next
version pair. Builds always run; Gradle can reuse unchanged task outputs.

The matrix continues after test errors, timeouts, or a failed configuration build. If any configuration
fails, the launcher returns a nonzero exit code. On completion or interruption, it stops the processes
it started and keeps collected results.

Main options:

- `--target` — required list of targets, separated by spaces or commas: `jvm`, `macos` (ARM64),
  `js`, `wasmJs`, or `ios` (ARM64 Simulator). Duplicate entries are ignored.
- `--compose-version` — a list of published Compose versions and/or `local`; defaults to one catalog
  version unless a core checkout is configured.
- `--kotlin-version` — a list of Kotlin and matching Compose Compiler versions; defaults to one catalog version.
- `--tests` — test names separated by spaces or commas; defaults to all tests.
- `--modes` — measurement modes; defaults to all four:
  - `startup` — startup time to the first frames;
  - `realFrames` — frame measurements in a real UI;
  - `averageFrame` — average offscreen rendering measurements;
  - `fps` — per-frame offscreen rendering measurements.
- `--repeat` — repetitions of each test/mode pair; default: `1`.
- `--timeout` — timeout per test in seconds, excluding build time; default: `300`.
- `--port` — a free port for the collector; default: `8090`.
- `--output-dir` — the base directory for results; it can already exist. Each invocation creates a new
  timestamped subdirectory for the whole version matrix.

Browser targets require `--browser` with a list of executable paths, commands on `PATH`, or macOS
`.app` bundles, separated by spaces or commas. Quote paths containing spaces. Duplicate entries are
ignored. Each selected browser runs on each selected web target; non-web targets ignore this list.
Chromium, Firefox, and Safari are detected from the executable name. Chromium and Firefox use temporary
profiles. Safari must be closed before the series and uses its normal profile without clearing the cache.
For iOS, select a simulator with `--simulator UUID`. Without this option, exactly one available
simulator must already be booted.

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
module. Both init modules provide the same `org.jetbrains.compose.benchmarks.skiko.configureBenchmarkSkiko()` function.
The app calls it at startup to configure `onBenchmarkComposeSceneInit` and, on JVM,
`getJvmGraphicsContext`. No reflection or service loading is needed.

The app declares `benchmarks-skiko-init-current` in `commonMain`. When the Compose version is
exactly `1.12.2`, Gradle substitutes `benchmarks-skiko-init-legacy` on all targets. The version comes
from `benchmarkComposeVersion` or the catalog default. Other versions and local core builds keep
current initialization. Skiko and its native libraries come from Compose and are not overridden.

Current initialization supports Metal/OpenGL/DirectX offscreen contexts on JVM. Legacy initialization
for Compose `1.12.2` uses DirectX on Windows and null-rendering on macOS/Linux, as in the original
multiplatform benchmarks, and leaves the scene initialization hook as a no-op. Offscreen GPU results
from different backends are not directly comparable. Other old Compose versions are not supported.

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

### 2. Register the case in `benchmarkContent`

In `benchmarks-app/src/commonMain/kotlin/org/jetbrains/compose/benchmarks/app/ArgumentsParser.kt`,
import the composable:

```kotlin
import org.jetbrains.compose.benchmarks.app.benchmarks.mybenchmark.MyBenchmark
```

Add a branch to the existing `when` in `benchmarkContent`, before `else`:

```kotlin
"MyBenchmark" -> { { MyBenchmark() } }
```

The outer branch returns a composable lambda; it must not call the composable directly. The string
is the case's identifier in launch arguments, JSON reports, and the HTML report. Names are
case-sensitive.

### 3. Register the case in the launcher

Add `"MyBenchmark"` to the `cases` list in
`benchmarks-launcher/src/main/kotlin/org/jetbrains/compose/benchmarks/launcher/Main.kt`.
Use exactly the same name as in `benchmarkContent`. This makes the case available to `--tests`,
includes it in `--tests all`, and exposes it in the launcher's help and validation.

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
`fps`) and platforms. For iOS, use `--target ios --simulator <simulator-uuid>` on macOS; for web,
select `--target js` or `--target wasmJs` and add `--browser`. Offscreen modes (`averageFrame` and
`fps`) must work without a platform window or user interaction.

### 6. Check the HTML report and diagnose failures

The launcher prints `HTML report: <path>` when report generation finishes. Open that `index.html`
in a browser, for example on macOS:

```bash
open "./out/new-case-results/<session-timestamp>/index.html"
```
