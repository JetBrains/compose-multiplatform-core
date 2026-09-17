/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.test

import kotlinx.coroutines.test.TestResult

/**
 * Invokes [block] as soon as the Skiko (Skia) runtime is initialized and it's safe to call Skia APIs.
 *
 * On most platforms the Skiko binaries are linked/loaded eagerly, so [block] is invoked directly. On
 * the Kotlin/JS target, however, `skiko.wasm` is fetched and instantiated asynchronously, and its
 * native symbols are published to the global scope only when that is done. Any Skia call made before
 * that fails with `ReferenceError: org_jetbrains_skia_... is not defined`, which is why the test
 * framework has to wait, the same way `ComposeViewport`/`CanvasBasedWindow` implicitly wait for
 * Skiko before starting an application.
 *
 * The waiting has to happen *outside* of `kotlinx.coroutines.test.runTest`: an extra suspension
 * point inside the test body would change the dispatching semantics observed by the test (e.g. it
 * would make an `UnconfinedTestDispatcher` queue eagerly launched coroutines instead of running them
 * immediately).
 */
internal expect fun onSkikoReady(block: () -> TestResult): TestResult
