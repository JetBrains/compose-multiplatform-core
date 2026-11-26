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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package androidx.compose.mpp.layoutinspector.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.mpp.layoutinspector.ComposeLayoutInspector
import androidx.compose.mpp.layoutinspector.RuntimeScopeInspector
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSApplicationDelegateProtocol
import platform.Foundation.NSNotification
import platform.darwin.NSObject
import platform.posix.fflush
import platform.posix.stdout

fun main(args: Array<String>) {
    val inspector = ComposeLayoutInspector()
    var runtimeInspector: RuntimeScopeInspector? = null
    val application = NSApplication.sharedApplication()
    val scope = CoroutineScope(Dispatchers.Main)
    application.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)
    val delegate = object : NSObject(), NSApplicationDelegateProtocol {
        override fun applicationShouldTerminateAfterLastWindowClosed(sender: NSApplication): Boolean = true

        override fun applicationWillTerminate(notification: NSNotification) {
            runBlocking { inspector.stop() }
        }
    }
    application.delegate = delegate

    try {
        val port = runBlocking { inspector.start() }
        Window(title = "Layout Inspector Demo", size = DpSize(360.dp, 250.dp)) {
            val composition = currentComposer.composition
            DisposableEffect(composition) {
                val collector = RuntimeScopeInspector(composition)
                runtimeInspector = collector
                onDispose {
                    runtimeInspector = null
                    collector.close()
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                var count by remember { mutableIntStateOf(0) }
                BasicText("Layout Inspector Demo", style = TextStyle(fontSize = 22.sp))
                BasicText("Agent listening on 127.0.0.1:$port")
                BasicText(
                    "Clicks: $count",
                    modifier = Modifier.clickable(role = Role.Button) { count++ }.padding(8.dp),
                )
                BasicText(
                    "Print scope counts",
                    modifier = Modifier.clickable(role = Role.Button) {
                        runtimeInspector?.printScopeCounts()
                    }.padding(8.dp),
                )
            }
        }
        println("INSPECTOR_READY:$port")
        fflush(stdout)

        if ("--stop-on-stdin" in args) {
            scope.launch(Dispatchers.IO) {
                // The JVM test closes its pipe to ask the app to exit.
                readlnOrNull()
                withContext(Dispatchers.Main) { application.terminate(null) }
            }
        }
        application.run()
    } finally {
        if (application.delegate === delegate) {
            application.delegate = null
        }
        scope.cancel()
        runtimeInspector?.close()
        runBlocking { inspector.stop() }
    }
}

private fun RuntimeScopeInspector.printScopeCounts() {
    val scopes = snapshot()
    println("Runtime scope counts (${scopes.size} scopes)")
    for (scope in scopes) {
        println(
            "Scope ${scope.id}: states=${scope.observedStates ?: "unknown"}, " +
                "invalidations=${scope.invalidations}, visits=${scope.visits}, skips=${scope.skips}"
        )
    }
    fflush(stdout)
}
