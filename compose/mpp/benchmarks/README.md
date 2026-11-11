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

`kotlin` sets the version of Kotlin and the matching Compose Compiler plugin.
`compose-multiplatform` sets the version of Compose libraries, resources, and the Compose Multiplatform
Gradle plugin. To compare other versions, change these values and rebuild the project.

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

When running an already built launcher, use `--compose-core-path /path/to/core`.
It passes the property to child Gradle builds, including the framework build started by Xcode.
A relative path in the Gradle property is resolved from this project. A relative path in the launcher
argument is resolved from the current working directory.

The included build replaces Compose library dependencies with core projects. Resources and the
Compose Multiplatform Gradle plugin still use published artifacts with the version from the catalog.

## Building the launcher

Build the launcher and copy the distribution:

```bash
./gradlew :benchmarks-launcher:installDist
cp -R benchmarks-launcher/build/install/benchmarks-launcher ./launcher
```

```bash
./launcher/bin/benchmarks-launcher --help
```

## Running benchmarks

The launcher builds the collector and the release application for the selected target once, starts
the collector, and runs tests sequentially. Each run gets a separate process and UUID.
After receiving a report, it closes the application and starts the next run.
The series continues after errors and timeouts. If any test fails, the launcher returns a nonzero
exit code. On completion or interruption, it stops the processes it started and keeps collected results.

Main options:

- `--target` — required target: `jvm`, `macos` (ARM64), `js`, `wasmJs`, or `ios` (ARM64 Simulator).
- `--tests` — test names separated by spaces or commas; defaults to all tests.
- `--modes` — measurement modes; defaults to all four:
  - `startup` — startup time to the first frames;
  - `realFrames` — frame measurements in a real UI;
  - `averageFrame` — average offscreen rendering measurements;
  - `fps` — per-frame offscreen rendering measurements.
- `--repeat` — repetitions of each test/mode pair; default: `1`.
- `--timeout` — timeout per test in seconds, excluding build time; default: `30`.
- `--port` — a free port for the collector; default: `8090`.
- `--output-dir` — a new directory for the series results; it must not exist already.
- `--skip-build` — use existing release application builds.

Browser targets require `--browser` with an executable path, a command on `PATH`, or a macOS `.app`.
Chromium, Firefox, and Safari are detected from the executable name. Chromium and Firefox use temporary
profiles. Safari must be closed before the series and uses its normal profile without clearing the cache.
For iOS, select a simulator with `--simulator UUID`. Without this option, exactly one available
simulator must already be booted.

### Example: JVM

Run two tests in startup and average rendering modes, with three repetitions:

```bash
./launcher/bin/benchmarks-launcher --target jvm  --tests LazyGrid TextLayout --modes startup averageFrame --repeat 3
```

### Example: Firefox

Measure offscreen rendering in a WasmJS application:

```bash
./launcher/bin/benchmarks-launcher --target wasmJs \
  --browser "/Applications/Firefox.app" \
  --tests LazyGrid --modes averageFrame
```

By default, each series creates a separate directory under `out/benchmark-reports`:

```text
<timestamp>-<target>-<unique suffix>/
    reports/       # JSON reports from all runs
    logs/          # Build, collector, and per-test logs
    summary.json   # Series parameters, statuses, errors, and report paths
```

`OUT_DIR` overrides the base directory for results and iOS DerivedData.
A relative `OUT_DIR` is resolved from the project root. Gradle artifacts remain in `<module>/build`.
