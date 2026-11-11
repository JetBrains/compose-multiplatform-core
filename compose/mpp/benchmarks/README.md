# Benchmarks

This is a standalone Gradle project. It is not included in the core build.
Run the commands below from `compose/mpp/benchmarks`.

## Versions and settings

`gradle/libs.versions.toml` defines Kotlin `2.4.21`, the matching Compose Compiler plugin,
and Compose Multiplatform `1.13.0-beta02`. Change the catalog to compare other compatible versions.
Ktor and the supporting Kotlin libraries also have their own catalog entries.

Repositories and Gradle/Kotlin properties follow the Demo project. The wrapper uses Gradle `9.5.0`
to match core: core's buildSrc currently fails with Gradle `9.7.1` deprecation warnings.
Configuration-cache problems are warnings, as in core, because its included build starts `xcrun`
during configuration. No Android target is configured for the benchmarks.

## Published libraries

By default, Compose libraries come from Maven repositories:

```bash
./gradlew :benchmarks-app:compileKotlinJvm :benchmarks-collector:installDist --console=plain
python3 launch-scripts/run_benchmarks.py --target jvm --tests LazyGrid --modes averageFrame startup
```

The launcher builds release/production artifacts once, starts the collector, runs tests sequentially,
and closes each test after its report or timeout. See [launcher options](launch-scripts/README.md)
for browser, native macOS, and iOS Simulator runs.

## Core sources with an included build

Pass a core checkout path to replace Compose Maven dependencies with core projects:

```bash
./gradlew :benchmarks-app:compileKotlinJvm -PcomposeCorePath=../../.. --console=plain
python3 launch-scripts/run_benchmarks.py \
  --target jvm --compose-core-path ../../.. --tests LazyGrid --modes averageFrame startup
```

Relative Gradle property paths are resolved from this project. Relative launcher paths are resolved
from the caller's working directory. An absolute path can select another checkout or worktree.
The launcher exports `ORG_GRADLE_PROJECT_composeCorePath` so Xcode's Gradle invocation receives it too.
For manual Xcode builds, set `composeCorePath` in this project's `gradle.properties` or export that
environment variable before opening Xcode. Omit the property to use published libraries.

Substitution covers the core Compose runtime, UI, foundation, animation, material, and desktop modules.
Compose resources still use the published artifact: they are not built in the core repository.
You can inspect the JVM dependency graph with:

```bash
./gradlew :benchmarks-app:dependencyInsight --configuration jvmRuntimeClasspath \
  --dependency org.jetbrains.compose.ui:ui -PcomposeCorePath=../../.. --console=plain
```

Kotlin in the benchmark project and Kotlin in core are configured independently. This project's
catalog does not override core's compiler or dependencies. Use a compatible core checkout and Gradle
version; rebuild the app when switching versions or switching between Maven and included-build modes.
Do not use `--skip-build` for that switch.

## Manual collector and JVM app

Start the collector in one terminal:

```bash
./gradlew :benchmarks-collector:run --args="8090 $PWD/out/reports" --console=plain
```

Run a case in another terminal:

```bash
./gradlew :benchmarks-app:run \
  --args="http://127.0.0.1:8090/reports LazyGrid averageFrame run-001" --console=plain
```

Application arguments are `<benchmarkCollectorUrl> <testName> <benchmarkType> <id>`.
Modes: `averageFrame`, `fps`, `realFrames`, `startup`. Use a unique ID for every run.
The collector listens on `127.0.0.1` and allows JSON POST requests from any origin.
Browser requests go directly to the collector, without a proxy. Loopback works for local browsers
and iOS Simulator, but not for a physical device connected to another machine.

The [iOS app](benchmarks-ios-app/README.md) builds its framework from this standalone project.
