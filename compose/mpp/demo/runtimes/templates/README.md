# Adding a New WebAssembly Runtime to Compose wasm-sdl

This directory documents how to add a new runtime adapter (e.g. WasmEdge, SpiderMonkey, V8, Wasmer, or embedded C++ runtime) to the Compose Multiplatform SDL runner.

## Architecture

Compose wasm-sdl runtimes adhere to the standardized Host-Runtime ABI (`runtimes/abi/host_abi.h`) and leverage the shared SDL2 & OpenGL lifecycle manager in `runtimes/core/` (`skiko-sdl-core`).

```
runtimes/
├── abi/                     # Common Host-Runtime ABI (C header)
├── core/                    # Shared native SDL2 window & GL core
├── node/                    # Node.js + N-API runtime adapter
├── wasmtime/                # Native Wasmtime C++ runtime adapter
└── <your-runtime>/          # New runtime adapter
```

## Step-by-Step Implementation

1. **Create Adapter Directory**:
   Create `compose/mpp/demo/runtimes/<runtime-name>/`.

2. **Implement Runtime Host / Linker Bindings**:
   Link the runtime's engine against `skiko-sdl-core`.
   Bind the standard Skiko and Compose host functions:
   - Windowing: create window, resize, present frame.
   - Graphics: desktop OpenGL / GLES function bindings.
   - Input: pointer and keyboard event dispatchers.
   - Timing: high-precision clock (`GetMonotonicTimeMillis()`) and animation frame scheduling.
   - Assets: font and manifest loader via `SkikoAssetLoader`.

3. **Provide Entry Point**:
   Provide either a native binary or launcher script in your runtime directory that instantiates `compose-mpp-demo.wasm` and runs the main frame loop.

4. **Register in Gradle**:
   In `compose/mpp/demo/build.gradle.kts`, register the runtime execution task (e.g. `wasmJsSdl<RuntimeName>Run`) and add it to `wasmJsSdlRun -PwasmSdlRuntime=<runtime-name>`.
