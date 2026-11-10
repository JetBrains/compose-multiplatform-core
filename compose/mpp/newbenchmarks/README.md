# Benchmarks

Run all commands from the repository root. Start the collector in one terminal, then start an app
in another terminal.

The apps currently display the collector URL and test name only. They do not run benchmarks or send reports yet.

## Collector

Pass the listening port and output directory:

```bash
./gradlew :compose:mpp:newbenchmarks:benchmarks-collector:run \
  --args="8090 $(pwd)/out/benchmark-reports" --console=plain
```

The endpoint is `POST http://127.0.0.1:8090/reports`. It accepts a benchmark report as JSON and saves it
as `[type]-[UTC ISO time].json` in the output directory. Keep this process running.

## Desktop (JVM)

Pass `bechmarkCollectorUrl` and the test name as two positional arguments:

```bash
./gradlew :compose:mpp:newbenchmarks:benchmarks-app:run \
  --args="http://127.0.0.1:8090/reports LazyGrid" --console=plain
```

## macOS (native ARM64)

On an Apple Silicon Mac, build the executable:

```bash
./gradlew :compose:mpp:newbenchmarks:benchmarks-app:linkDebugExecutableMacosArm64 --console=plain
```

Then run it with the same two arguments:

```bash
./out/compose-multiplatform-core/compose/mpp/newbenchmarks/benchmarks-app/build/bin/macosArm64/debugExecutable/benchmarks-app.kexe \
  http://127.0.0.1:8090/reports LazyGrid
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

Open the address printed by the development server and add the `bechmarkCollectorUrl` and `test` query parameters.
For example, if it uses port 8080:

```text
http://localhost:8080/?bechmarkCollectorUrl=http%3A%2F%2F127.0.0.1%3A8090%2Freports&test=LazyGrid
```

URL-encode both parameter values. If the server opens a page without parameters, add them to the
address and reload the page.

## iOS (ARM64 simulator or device)

On macOS with Xcode installed:

```bash
open compose/mpp/newbenchmarks/benchmarks-ios-app/BenchmarksIosApp.xcodeproj
```

1. Select the **BenchmarksIosApp** scheme.
2. Select an ARM64 iOS simulator or a connected iOS device.
3. Open **Product → Scheme → Edit Scheme → Run → Arguments**.
4. Add two enabled launch arguments, in this order:
   - `http://127.0.0.1:8090/reports` (`bechmarkCollectorUrl`)
   - `LazyGrid`
5. Run the app. Xcode builds the `BenchmarksApp` Kotlin framework automatically.

The shared scheme already contains these example arguments. For a physical device, select your
team under **Signing & Capabilities**. See [iOS app setup](benchmarks-ios-app/README.md) for more details.

## Current network limits

- The collector listens on `127.0.0.1` only. It is accessible from the same Mac and its iOS simulator,
  but not from a physical iOS device. Future device reporting will need a reachable host address
  and a collector configured to listen on a network interface.
- Browser reporting will need CORS support in the collector. It is not configured yet.
- These limits do not affect the current apps, which only display the input parameters.
