/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.desktop.examples.kdt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.ReactiveStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.runApplicationBlocking
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val store = ReactiveStore().also {
    DataSource.register(it)
    it.set("count", 0)
}

fun main() {
    runApplicationBlocking(
        identifier = System.getProperty("kdt.application.identifier") ?: "compose-application",
    ) {
        AppWindow()
    }
}


@Composable
private fun AppWindow() {
    var isWindowShown by remember { mutableStateOf(true) }
    if (isWindowShown) {
        Window(onCloseRequested = { isWindowShown = false }) {
            Column(Modifier.padding(24.dp).background(Color.White)) {
                RecomposeCount("live") { n ->
                    val count = store.get("count") as Int
                    Text("count = $count   (recomposed $n)", color = randomColor())
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    store.set("count", (store.get("count") as Int) + 1)
                }) {
                    Text("increment", color = randomColor())
                }
                Column(Modifier.border(1.dp, Color.Blue).padding(5.dp)) {
                    var counter by remember { mutableStateOf(0) }
                    Text("State counter $counter", color = randomColor())
                    Button(onClick = {
                        counter += 1
                    }) {
                        Text("increment state", color = randomColor())
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("static", color = randomColor())
                Spacer(Modifier.height(12.dp))
                Box {
                    val ignored = DataSource.withoutReadObservation { store.get("count") as Int }
                    Text("ignored = $ignored", color = randomColor())
                }
            }
        }
    }
}

fun randomColor() = Color(
    Random.nextInt(256),
    Random.nextInt(256),
    Random.nextInt(256),
    alpha = 255
)

@Composable
private fun RecomposeCount(label: String, content: @Composable (Int) -> Unit) {
    val counter = remember { intArrayOf(0) }
    counter[0]++
    println("recompose[$label] = ${counter[0]}")
    content(counter[0])
}
