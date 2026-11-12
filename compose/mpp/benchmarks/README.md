# Compose benchmarks

A project with Compose Multiplatform benchmarks for measuring application startup and rendering
performance. This is a standalone Gradle build, separate from the core build.

Run all commands below from `compose/mpp/benchmarks`. The launcher runs on macOS and Linux
and requires JDK 17 or newer. Apple targets require macOS with Xcode.

## Modules

- `benchmarks-app` — an application with Compose benchmark cases.
- `benchmarks-runtime` — runs measurements and sends results.
- `benchmarks-report` — shared report models.
- `benchmarks-collector` — receives reports over HTTP and saves them as JSON files.
- `benchmarks-launcher` — a console application that builds and runs benchmarks sequentially.
- `benchmarks-ios-app` — an iOS host application for benchmarks; a separate Xcode project.

## Compose and Kotlin versions

Set the versions of published libraries and plugins in the `gradle/libs.versions.toml` version catalog:

```toml
[versions]
kotlin = "2.4.21"
compose-multiplatform = "1.13.0-beta02"
```

`kotlin` sets the default version of Kotlin and the matching Compose Compiler plugin.
`compose-multiplatform` sets the default version of Compose libraries, resources, and the Compose
Multiplatform Gradle plugin. Keep a published Compose version in this catalog.

Use `--kotlin-version` and `--compose-version` to pass lists of versions without editing the catalog
or rebuilding the launcher. Lists accept spaces or commas. The launcher runs every Kotlin × Compose
combination sequentially in one timestamped session. Duplicate entries are ignored.
A published Compose version also selects that version of the resources and Gradle plugin.
Use compatible Kotlin and Compose versions.

### Using core sources

You can use a core checkout as an included build instead of published Compose libraries.
Add this property to `gradle.properties`:

```properties
composeCorePath=../../..
```

Or pass the property on the Gradle command line:

```bash
./gradlew :benchmarks-launcher:installDist -PcomposeCorePath=/path/to/core --console=plain
```

In the launcher, use `--compose-version local` to select the current core checkout at `../../..`.
Add `--compose-core-path /path/to/core` to select another checkout or worktree. The existing core path
option or property also selects local Compose when `--compose-version` is omitted. Published entries
in a version list disable substitution even if the property is configured. `--compose-core-path`
requires `local` in the list and applies only to local configurations.

The launcher passes the resolved version numbers and core path to child Gradle builds, including
Xcode's framework build. Only a non-empty `composeCorePath` enables the included build; the launcher
passes an empty value for published configurations to override any configured path. A relative path in the Gradle property is resolved from this project. A relative path
in the launcher argument is resolved from the current working directory.

For a `local` entry, the resolved Compose version is taken from the catalog, with core projects
substituted through the included build. Resources and the Compose Multiplatform Gradle plugin also
use that catalog version. `--kotlin-version` changes Kotlin in the benchmark project only;
core uses its own compiler version.

## Building the launcher

Build the launcher and copy the distribution:

```bash
./gradlew :benchmarks-launcher:installDist
mkdir -p launcher
cp -R benchmarks-launcher/build/install/benchmarks-launcher/. ./launcher/
```

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

By default, results go under `out/benchmark-reports`. `--output-dir` replaces this base directory.
Each invocation creates a UTC timestamp directory in `yyyy-MM-dd-HH-mm-ss-SSS` format, then groups
results by versions and target:

```text
<date>-<time>/
    index.html             # Interactive charts for this session
    kotlin(2.4.21)-cmp(1.13.0-beta02)-reports/
        jvm/
            reports/       # JSON reports from all runs
            logs/          # Build, collector, and per-test logs
            summary.json   # Versions, parameters, statuses, errors, and report paths
```

All configurations share the same timestamp directory, each with its own version/target subtree.
Web targets have an additional `browser-<index>-<name>` subdirectory containing their `reports`,
`logs`, and `summary.json`, so results from different browsers never overwrite each other.
All targets and browsers are included in one HTML report. Selecting a web target shows an additional
browser switch with browser names; non-web targets hide it.
For local Compose, the version directory is named `kotlin(<version>)-cmp(local)-reports`.
Its summary records the actual Compose version from the catalog, `localCompose: true`, and the core
checkout path. Published configurations have `localCompose: false`. Existing results are not overwritten.

## Target environment collection

The app has a separate service mode that collects target-process metadata without creating
benchmark content, opening a benchmark window, or running measurements. It is not a benchmark type
and requires no test name. The launcher does not invoke this mode or include its output in HTML yet.

Start the collector, then invoke the app with its environment endpoint:

```bash
./gradlew :benchmarks-collector:installDist --console=plain
./benchmarks-collector/build/install/benchmarks-collector/bin/benchmarks-collector 8090 ./environment-results

# In another terminal (JVM):
./gradlew :benchmarks-app:run \
  --args="environment target-jvm http://127.0.0.1:8090/environment" --console=plain
```

Native macOS executables and the iOS app accept the same three arguments:

```text
environment <id> <environmentCollectorUrl>
```

Benchmark launches use `<benchmark-type> <id> <benchmarkCollectorUrl> <test-name>`.

For JS and WasmJS, open the app with these query parameters (no `test` parameter):

```text
?type=environment&id=target-web&benchmarkCollectorUrl=http%3A%2F%2F127.0.0.1%3A8090%2Fenvironment
```

## HTML reports

The launcher's final step generates `index.html` in the current timestamp directory and prints its
path. Open it manually in a browser. All measurement data is embedded in the file; no local server
is required. The minified Chart.js 4.4.8 library is bundled in the launcher
resources and embedded in the HTML, so reports work offline as a single file.
HTML is generated with kotlinx.html.

`OUT_DIR` overrides the base directory for results and iOS DerivedData.
A relative `OUT_DIR` is resolved from the project root. Gradle artifacts remain in `<module>/build`.
