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

package org.jetbrains.compose.benchmarks.app

import androidx.compose.ui.window.ComposeViewport
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.asJsException
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.benchmarks.reportTargetEnvironment
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import org.w3c.dom.url.URLSearchParams

@OptIn(ExperimentalWasmJsInterop::class)
fun main() {
    val startupTiming = captureStartupTiming()
    val parameters = URLSearchParams(window.location.search.toJsString())
    val benchmarkCollectorUrl = requireNotNull(parameters.get("benchmarkCollectorUrl")) { "Missing query parameter: benchmarkCollectorUrl" }
    val id = requireNotNull(parameters.get("id")) { "Missing query parameter: id" }
    if (parameters.get("type") == "environment") {
        MainScope().launch { reportTargetEnvironment(benchmarkCollectorUrl, id) }
        return
    }
    val testName = requireNotNull(parameters.get("test")) { "Missing query parameter: test" }
    val benchmarkType = parseBenchmarkType(
        requireNotNull(parameters.get("type")) { "Missing query parameter: type" }
    )
    if (benchmarkType.isRenderBenchmark()) {
        MainScope().launch {
            suspendAwaitSkiko()
            runRenderBenchmark(benchmarkCollectorUrl, testName, benchmarkType, id)
        }
    } else {
        ComposeViewport("root") {
            App(benchmarkCollectorUrl, testName, benchmarkType, startupTiming, id)
        }
    }
}

@OptIn(InternalSkikoApi::class, ExperimentalWasmJsInterop::class)
internal suspend fun suspendAwaitSkiko(): JsAny = suspendCancellableCoroutine { cont ->
    awaitSkiko.then(
        onFulfilled = { cont.resume(it); null },
        onRejected = { cont.resumeWithException(it.asJsException()); null },
    )
}
