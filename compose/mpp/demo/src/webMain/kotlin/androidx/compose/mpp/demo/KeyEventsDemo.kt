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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class KeyEventRecord(
    val key: Key?,
    val eventType: KeyEventType,
    val id: Int
)

@Composable
fun KeyEventsDemo() {
    var isFocused by remember { mutableStateOf(false) }
    val keyEvents = remember { mutableStateListOf<KeyEventRecord>() }
    var eventCount by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Click the box below to focus it, then press keys on your keyboard.",
            modifier = Modifier.padding(bottom = 16.dp),
            fontWeight = FontWeight.Medium,
        )

        // Focusable box that highlights when focused
        Box(
            modifier = Modifier
                .size(200.dp, 100.dp)
                .background(
                    color = if (isFocused) Color(0xFFBBDEFB) else Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(12.dp),
                )
                .border(
                    width = 2.dp,
                    color = if (isFocused) Color(0xFF1976D2) else Color(0xFFBDBDBD),
                    shape = RoundedCornerShape(12.dp),
                )
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                }
                .clickable { /* click to request focus */ }
                .onKeyEvent { event ->
                    eventCount++
                    keyEvents.add(0, KeyEventRecord(event.key, event.type, eventCount))
                    if (keyEvents.size > 50) {
                        keyEvents.removeAt(keyEvents.size - 1)
                    }
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isFocused) "Focused" else "Click to focus",
                color = if (isFocused) Color(0xFF0D47A1) else Color(0xFF757575),
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Key Events (newest first):",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Lazy list showing captured key events
        val listState = rememberLazyListState()
        LaunchedEffect(eventCount) {
            listState.scrollToItem(0)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(4.dp),
        ) {
            items(keyEvents, key = { it.id }) { record ->
                Row(
                    modifier = Modifier
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = record.key?.toString() ?: "Unknown",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when (record.eventType) {
                            KeyEventType.KeyDown -> "KeyDown"
                            KeyEventType.KeyUp -> "KeyUp"
                            else -> "Unknown"
                        },
                        color = Color(0xFF555555),
                    )
                }
            }
        }
    }
}
