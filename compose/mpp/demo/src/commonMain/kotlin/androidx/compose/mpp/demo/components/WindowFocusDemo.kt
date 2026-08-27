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

package androidx.compose.mpp.demo.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WindowFocusDemo() {
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    var focusGainedCount by remember { mutableIntStateOf(0) }
    var focusLostCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(isWindowFocused) {
        if (isWindowFocused) {
            focusGainedCount++
        } else {
            focusLostCount++
        }
        println("Focus changed: isWindowFocused=$isWindowFocused")
    }

    // Lifecycle state tracking
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow
        .collectAsStateWithLifecycle(lifecycle.currentState)
    val isResumed = lifecycleState >= Lifecycle.State.RESUMED

    val textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isWindowFocused) "🟢 Window Focused" else "🔴 Window Unfocused",
            style = textStyle
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "🔍 Focus gained count: $focusGainedCount",
            style = textStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "💨 Focus lost count: $focusLostCount",
            style = textStyle
        )
        Spacer(Modifier.height(32.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "⚙️ Lifecycle vs WindowInfo",
            style = textStyle
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "📋 Lifecycle.State: $lifecycleState",
            style = textStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isResumed) "▶️ RESUMED" else "⏸️ NOT RESUMED",
            style = textStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isWindowFocused == isResumed) "✅ Both agree" else "⚠️ They differ!",
            style = textStyle
        )
    }
}
