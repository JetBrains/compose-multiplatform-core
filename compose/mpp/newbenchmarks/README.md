# Benchmarks

Run all commands from the repository root. Start the collector in one terminal, then start an app
in another terminal.

`averageFrame` and `fps` run offscreen without creating an application window or Compose UI host.
The benchmark case is composed only inside the offscreen scene. Both modes use a 1920×1080 scene,
120 target FPS, 100 warmup frames, and 1000 measured frames, then send a report to the collector.
JVM and native macOS runs finish after the report is sent. Browser and iOS runs keep their platform
application alive without creating a UI host.

Use `realFrames` for measurement in a real UI host, or `startup` for its first frame and 30 additional
frame-clock events without warmup. All launch examples below use `averageFrame`; replace it with
`fps`, `realFrames`, or `startup` to select another mode.

Startup timings use a monotonic mark captured before arguments and UI are processed. On iOS, the
mark is captured at the first Kotlin entry from `AppDelegate.init`. `timeToFirstFrame` and
`timeToNthFrame` are measured from this mark, not from process startup. They observe the Compose
frame clock, not GPU completion or screen presentation. `timeToMain` measures process-to-entry time:
JVM uses VM uptime as an approximation, Apple uses the process start timestamp, and browsers use
the page's navigation start, reconstructed from `performance.now()`. On the web, `timeToMain` therefore
measures page navigation to Kotlin entry, not browser process startup.
Restart the application for each cold startup measurement.

Case names are case-sensitive: `AnimatedVisibility`, `LazyGrid`, `LazyGrid-ItemLaunchedEffect`,
`LazyGrid-SmoothScroll`, `LazyGrid-SmoothScroll-ItemLaunchedEffect`, `VisualEffects`, `LazyList`,
`MultipleComponents`, `MultipleComponents-NoVectorGraphics`, `TextLayout`, `CanvasDrawing`, `HeavyShader`.

## Collector

Pass the listening port and output directory:

```bash
./gradlew :compose:mpp:newbenchmarks:benchmarks-collector:run \
  --args="8090 $(pwd)/out/benchmark-reports" --console=plain
```

The endpoint is `POST http://127.0.0.1:8090/reports`. It accepts a benchmark report as JSON with
`id`, `name`, `type`, and the mode-specific results. The app uses the supplied launch `id` as the
report ID and the case name as `name`. Use a unique ID, such as a UUID, for each run. The collector saves it as `[id]-[type]-[UTC ISO time].json` in the output directory.
The `id` must contain 1–128 ASCII letters, digits, underscores, or hyphens.
It writes a temporary `.tmp` file first, then publishes the complete report with an atomic rename.
Watch only `*.json` files when waiting for reports. Keep this process running.

## Desktop (JVM)

Pass `benchmarkCollectorUrl`, the test name, the benchmark type, and `id` as four positional arguments:

```bash
./gradlew :compose:mpp:newbenchmarks:benchmarks-app:run \
  --args="http://127.0.0.1:8090/reports LazyGrid averageFrame run-001" --console=plain
```

## macOS (native ARM64)

On an Apple Silicon Mac, build the executable:

```bash
./gradlew :compose:mpp:newbenchmarks:benchmarks-app:linkDebugExecutableMacosArm64 --console=plain
```

Then run it with the same four arguments:

```bash
./out/compose-multiplatform-core/compose/mpp/newbenchmarks/benchmarks-app/build/bin/macosArm64/debugExecutable/benchmarks-app.kexe \
  http://127.0.0.1:8090/reports LazyGrid averageFrame run-001
```

This path assumes the default repository `out` directory. Adjust it if using a custom `OUT_DIR`.

## Browser (JS or WasmJS)

Start one development server at a time.

JS:

```bash
./gradlew :compose:mpp:newbenchmarks:benchmarks-app:jsBrowserDevelopmentRun --console=plain
```

WasmJS:

```bash
./gradlew :compose:mpp:newbenchmarks:benchmarks-app:wasmJsBrowserDevelopmentRun --console=plain
```

Open the address printed by the development server and add the `benchmarkCollectorUrl`, `test`, `type`, and `id` query parameters.
For example, if it uses port 8080:

```text
http://localhost:8080/?benchmarkCollectorUrl=http%3A%2F%2F127.0.0.1%3A8090%2Freports&test=LazyGrid&type=averageFrame&id=run-001
```

URL-encode parameter values. If the server opens a page without parameters, add them to the
address and reload the page.

## iOS (ARM64 simulator or device)

On macOS with Xcode installed:

```bash
open compose/mpp/newbenchmarks/benchmarks-ios-app/BenchmarksIosApp.xcodeproj
```

1. Select the **BenchmarksIosApp** scheme.
2. Select an ARM64 iOS simulator or a connected iOS device.
3. Open **Product → Scheme → Edit Scheme → Run → Arguments**.
4. Add four enabled launch arguments, in this order:
   - `http://127.0.0.1:8090/reports` (`benchmarkCollectorUrl`)
   - `LazyGrid`
   - `averageFrame`
   - `run-001` (`id`)
5. Run the app. Xcode builds the `BenchmarksApp` Kotlin framework automatically.

The shared scheme already contains these example arguments. For a physical device, select your
team under **Signing & Capabilities**. See [iOS app setup](benchmarks-ios-app/README.md) for more details.

## Current network limits

- The collector listens on `127.0.0.1` only. It is accessible from the same Mac and its iOS simulator,
  but not from a physical iOS device. Future device reporting will need a reachable host address
  and a collector configured to listen on a network interface.
- Browser reporting will need CORS support in the collector. It is not configured yet.
- Without the required network setup, browser and physical-device runs cannot deliver reports.
