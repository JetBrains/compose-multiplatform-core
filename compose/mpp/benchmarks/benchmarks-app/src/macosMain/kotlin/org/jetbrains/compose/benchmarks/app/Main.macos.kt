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

import androidx.compose.ui.window.Window
import kotlinx.coroutines.runBlocking
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSApplicationDelegateProtocol
import platform.darwin.NSObject

fun main(args: Array<String>) {
    val startupTiming = captureStartupTiming()
    val (benchmarkCollectorUrl, testName, benchmarkType, id) = parseArguments(args)
    if (benchmarkType.isRenderBenchmark()) {
        runBlocking {
            runRenderBenchmark(benchmarkCollectorUrl, testName, benchmarkType, id)
        }
        return
    }
    val application = NSApplication.sharedApplication()
    application.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)
    application.delegate = object : NSObject(), NSApplicationDelegateProtocol {
        override fun applicationShouldTerminateAfterLastWindowClosed(sender: NSApplication): Boolean {
            return true
        }
    }
    Window("Benchmarks") {
        App(benchmarkCollectorUrl, testName, benchmarkType, startupTiming, id)
    }
    application.run()
}
