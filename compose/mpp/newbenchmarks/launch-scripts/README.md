# Benchmark launcher

`run_benchmarks.py` uses Python 3 and its standard library. No Playwright or Python packages are needed.
It runs on macOS and Linux. Apple targets require macOS and Xcode.

Run these examples from the repository root. The script also works from another working directory.

## Defaults

- `--target` is required: `jvm`, `macos`, `js`, `wasmJs`, or `ios` (iOS Simulator).
- All 12 cases and all four modes (`averageFrame`, `fps`, `realFrames`, `startup`).
- One fresh application process and UUID per case/mode/repetition.
- `--repeat 1`, `--timeout 30` seconds per test, collector `--port 8090`.
- Build the collector and selected app once before the series. Apps use release/production artifacts.
  Build time is not part of the test timeout. Use `--skip-build` to reuse existing artifacts.
- Continue after failed tests and timeouts. Exit with code 1 if any test fails; Ctrl+C exits with code 130.
- Reports go directly into one shared `reports` folder. The script waits for a matching report ID,
  name, and type, then closes the test process before starting the next one.
- Stop the collector and web server at the end, including on interruption. Existing browser sessions
  are not reused or closed. An iOS Simulator is left booted.

The collector port must be free: this script starts its own collector, not an existing one.
Do not run two launchers against the same simulator application at the same time.

## JVM

Run everything:

```bash
python3 compose/mpp/newbenchmarks/launch-scripts/run_benchmarks.py --target jvm
```

Select cases and modes, with three repetitions of each combination:

```bash
python3 compose/mpp/newbenchmarks/launch-scripts/run_benchmarks.py \
  --target jvm --tests LazyGrid TextLayout --modes averageFrame startup --repeat 3
```

Lists accept spaces or commas. `--tests all` and `--modes all` restore the defaults.
The JVM app is launched directly from its release uber-JAR, not through a Gradle run task.

## Native macOS (ARM64)

```bash
python3 compose/mpp/newbenchmarks/launch-scripts/run_benchmarks.py \
  --target macos --tests LazyGrid --modes averageFrame fps
```

## Browser (JS or WasmJS)

Pass a browser executable, a command on `PATH`, or a macOS `.app` path.
Set `--browser-kind firefox` for Firefox; the default is `chromium`.
Each test gets a temporary browser profile and a separate instance. It is closed after a report,
crash, or timeout. Browser launches are visible by default.

Chromium:

```bash
python3 compose/mpp/newbenchmarks/launch-scripts/run_benchmarks.py \
  --target wasmJs --browser "/Applications/Google Chrome.app" \
  --tests LazyGrid --modes startup averageFrame
```

Firefox:

```bash
python3 compose/mpp/newbenchmarks/launch-scripts/run_benchmarks.py \
  --target js --browser "/Applications/Firefox.app" --browser-kind firefox \
  --tests LazyGrid --modes averageFrame
```

Use `--browser-arg=--some-flag` to pass extra browser flags. For Chromium, for example:
`--browser-arg=--js-flags=--expose-gc` enables manual GC.

The launcher serves production files on a free loopback port (`--web-port` can override it).
The browser sends reports directly to the collector port. There is no proxy: the collector allows
JSON POST requests from any origin and handles CORS preflight requests.
The browser receives all application parameters in the page URL.

## iOS Simulator (ARM64)

Without `--simulator`, exactly one available simulator must already be booted:

```bash
python3 compose/mpp/newbenchmarks/launch-scripts/run_benchmarks.py \
  --target ios --tests LazyGrid --modes startup
```

Or select a simulator by UUID (recommended) or a unique name. The launcher boots it if necessary:

```bash
python3 compose/mpp/newbenchmarks/launch-scripts/run_benchmarks.py \
  --target ios --simulator YOUR_SIMULATOR_UUID --tests LazyGrid --modes startup --repeat 3
```

The launcher builds the Release Xcode app, installs it once, then uses `simctl launch --console`
with the four application arguments. Each run terminates any previous instance of this benchmark
app on the selected simulator. After the report or timeout, it calls `simctl terminate`.
Physical iOS devices are not supported by this launcher.

## Output and logs

By default, each series creates a new directory:

```text
out/benchmark-reports/<UTC timestamp>-<target>-<unique suffix>/
    reports/       # All collected benchmark JSON files, prefixed with the run UUID
    logs/          # Build logs, collector log, and a separate log for each run UUID
    summary.json   # Parameters, statuses, timings, report paths, and errors
```

Set `--output-dir /path/to/new-directory` to choose the series directory. It must not already exist.
Use `--port 18090` if port 8090 is occupied. `OUT_DIR` is respected for Gradle artifacts and default output.
The default iOS DerivedData path is `$OUT_DIR/benchmarks-ios-derived` (or `out/benchmarks-ios-derived`).

A test timeout limits launch/report waiting; process cleanup may take a few extra seconds.
Reports already collected are kept after failures or interruption. Inspect `summary.json` and the
per-test logs to diagnose failures. For application details and case names, see the [main README](../README.md).
