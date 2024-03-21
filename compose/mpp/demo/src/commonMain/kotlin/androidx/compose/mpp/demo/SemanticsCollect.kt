/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.actionContext.ActionContextCollector
import androidx.compose.ui.actionContext.UniqueDataKey
import androidx.compose.ui.actionContext.actionContextCollector
import androidx.compose.ui.actionContext.focusData

private val CounterSemantics = UniqueDataKey<Int>("CounterDataKey")

@Composable
fun ActionContextCollectExample() {
    val counter = remember { mutableStateOf(0) }
    Row {
        Button(onClick = {
            counter.value++
        }) {
            Text("Counter with value ${counter.value}")
        }
        Row(modifier = Modifier.focusData {
            put(CounterSemantics, counter.value)
        }) {
            val focusDataState = remember { mutableStateOf<Int?>(null) }
            val actionContextCollector = remember { ActionContextCollector() }
            Text(
                "Focus data contains counter: ${focusDataState.value}",
                modifier = Modifier.actionContextCollector(actionContextCollector)
            )
            Button(onClick = {
                focusDataState.value =
                    actionContextCollector.collectActionContext()[CounterSemantics]
            }) {
                Text("Collect focus data")
            }
        }
    }
}