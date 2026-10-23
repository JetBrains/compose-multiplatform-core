# IntObjectMap measurements for the web demo

This optional module compiles a generated, instrumented copy of the checked-in collection sources.
The original collection sources are left untouched. With `-Pcompose.demo.profileIntObjectMaps=true`,
the fork build substitutes this module for the AndroidX collection dependency throughout the build, so
maps created inside Compose and other dependencies are measured too. Use this flag only with the
JS/Wasm profiling tasks below; the replacement does not provide desktop or native variants.

Each constructed `MutableIntObjectMap` contributes one sample: its greatest entry count during
the measurement. Empty maps contribute zero. Removal, clearing, and replacing an existing value
do not reduce the recorded peak. Percentiles use linear interpolation between sorted samples;
the median is p50. The collector retains entry counts and optional stack strings, without retaining
maps or their contents.

## Verify the collector

Compile the tests first:

```shell
./gradlew :collection:collection:compileTestDevelopmentExecutableKotlinJs \
  :collection:collection:compileTestDevelopmentExecutableKotlinWasmJs --console=plain
```

Then run the controlled tests on both runtimes:

```shell
./gradlew :collection:collection:cleanAllTests \
  :collection:collection:jsNodeTest :collection:collection:wasmJsNodeTest \
  --no-build-cache --console=plain
```

The tests assert known distributions, including `[0, 2, 4, 8, 16]` (max 16, median 4, p75 8),
and check that removals and repeated overwrites do not distort the results.

## Measure a demo workload

Compile the browser test first:

```shell
./gradlew :compose:mpp:demo:compileTestDevelopmentExecutableKotlinJs \
  -Pcompose.demo.profileIntObjectMaps=true --console=plain
```

Run with Chrome installed (`CHROME_BIN` can select a custom browser executable):

```shell
./gradlew :compose:mpp:demo:cleanAllTests :compose:mpp:demo:jsBrowserTest \
  -Pcompose.demo.profileIntObjectMaps=true --no-build-cache --console=plain
```

For Wasm, replace `compileTestDevelopmentExecutableKotlinJs` with
`compileTestDevelopmentExecutableKotlinWasmJs`, and `jsBrowserTest` with `wasmJsBrowserTest`.

The test composes the demo's `App`, visits `Example1`, clicks its button, replaces text in its
text field, and returns to the main screen, three times. It prints `INT_OBJECT_MAP_STATS` with
instance count, maximum, median, and p75, followed by a histogram of per-instance peaks.
It also enables `IntObjectMapStats.captureCallStacks` for the workload. The
`INT_OBJECT_MAP_TRACE_START` blocks group maps with identical construction and peak-insertion stacks,
and print each group's instance count and peak-size histogram. `CREATION` identifies the allocator;
`PEAK_WRITE` identifies the insertion that established the observed peak. An empty map has no
peak-insertion stack. Stack collection is disabled by default outside this demo test.
It asserts that the instrumentation observed maps and insertions, rather than guessing a size
limit before measuring the workload. The controlled tests separately verify the calculations.

The checkpoint excludes instances created before the test starts. Results describe this specific
scenario in the Compose test scene, not every screen or browser interaction in the demo.
The instrumentation also changes allocation and timing behavior; use these runs for entry counts.
