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
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko

/**
 * Chains the test onto the promise of the Skiko runtime loading. `TestResult` is a `Promise` on
 * Kotlin/JS, and the promise returned by `then` is resolved only when the promise returned by
 * [block] is, so the test runner still waits for the test itself to complete.
 */
@OptIn(InternalSkikoApi::class, ExperimentalWasmJsInterop::class)
internal actual fun onSkikoReady(block: () -> TestResult): TestResult =
    awaitSkiko.then { block() }.unsafeCast<TestResult>()
