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

package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private const val CoroutineCount = 100_000
private const val YieldsPerCoroutine = 10


// https://youtrack.jetbrains.com/issue/CMP-10751
@Composable
fun CrashAfterResizeDemo() {
    val compositionScope = rememberCoroutineScope()
    var attempts by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Main dispatcher / resize trampoline race")
        Button(
            onClick = {
                compositionScope.launch { scheduleCoroutines() }
                attempts++
            }
        ) {
            Text("Overload Main and then resize")
        }
        Text("Attempts: $attempts")
        Text("Queued work per attempt: $CoroutineCount coroutines × $YieldsPerCoroutine yields")
    }
}

private suspend fun CoroutineScope.scheduleCoroutines() {
    repeat(CoroutineCount) {
        launch(Dispatchers.Main) {
            repeat(YieldsPerCoroutine) {
                yield()
            }
        }
    }
    delay(1000.milliseconds)
    scheduleCoroutines()
}
